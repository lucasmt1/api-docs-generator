package dev.apidocs.core.extraction;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.ConstantResolver;
import dev.apidocs.core.analysis.Javadocs;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ParameterLocation;
import dev.apidocs.core.model.RequestBodyInfo;
import dev.apidocs.core.model.ResponseInfo;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/** Code Extractor for the Controllers layer: endpoints, parameters, bodies, statuses, security and calls. */
public final class ControllerExtractor {

    private static final Map<String, HttpMethod> SHORTCUT_MAPPINGS = Map.of("GetMapping", HttpMethod.GET,
            "PostMapping", HttpMethod.POST, "PutMapping", HttpMethod.PUT, "PatchMapping", HttpMethod.PATCH,
            "DeleteMapping", HttpMethod.DELETE);
    private static final Set<String> FRAMEWORK_PARAMETERS = Set.of("HttpServletRequest", "HttpServletResponse",
            "ServletRequest", "ServletResponse", "HttpSession", "Principal", "Authentication", "Model", "ModelMap",
            "BindingResult", "Errors", "Locale", "TimeZone", "ZoneId", "UriComponentsBuilder", "ServerWebExchange",
            "ServerHttpRequest", "ServerHttpResponse", "WebRequest", "NativeWebRequest", "Sort", "HttpHeaders",
            "SessionStatus", "RedirectAttributes", "Jwt", "OAuth2User", "UserDetails");
    private static final String[] SKIPPED_PARAMETER_ANNOTATIONS = {"AuthenticationPrincipal", "CurrentSecurityContext",
            "RequestAttribute", "SessionAttribute", "CookieValue", "MatrixVariable", "RequestPart"};
    private static final Set<String> MAP_TYPES = Set.of("Map", "MultiValueMap", "HashMap", "LinkedHashMap",
            "HttpHeaders");
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "APPLICATION_JSON_VALUE", "application/json", "APPLICATION_XML_VALUE", "application/xml",
            "TEXT_PLAIN_VALUE", "text/plain", "MULTIPART_FORM_DATA_VALUE", "multipart/form-data",
            "APPLICATION_OCTET_STREAM_VALUE", "application/octet-stream", "TEXT_EVENT_STREAM_VALUE", "text/event-stream",
            "APPLICATION_FORM_URLENCODED_VALUE", "application/x-www-form-urlencoded",
            "APPLICATION_PROBLEM_JSON_VALUE", "application/problem+json");
    private static final Set<String> RESPONSE_WRAPPERS = Set.of("ResponseEntity", "HttpEntity", "CompletableFuture",
            "CompletionStage", "Mono", "DeferredResult", "Callable", "WebAsyncTask", "Optional", "ListenableFuture");

    private record Mapping(List<HttpMethod> methods, List<String> paths, List<String> consumes, List<String> produces) {
    }

    private final TypeIndex index;
    private final TypeResolver resolver;
    private final ConstantResolver constants;
    private final String contextPath;
    private final Set<String> serviceNames;

    public ControllerExtractor(TypeIndex index, TypeResolver resolver, ConstantResolver constants, String contextPath,
            Set<String> serviceNames) {
        this.index = index;
        this.resolver = resolver;
        this.constants = constants;
        this.contextPath = contextPath;
        this.serviceNames = Set.copyOf(serviceNames);
    }

    public List<ControllerInfo> extract(List<Warning> warnings) {
        List<ControllerInfo> controllers = new ArrayList<>();
        for (TypeIndex.IndexedType type : index.all()) {
            if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration) || declaration.isInterface()) {
                continue;
            }
            boolean restController = Annotations.has(declaration, "RestController");
            if (!restController && !Annotations.has(declaration, "Controller")) {
                continue;
            }
            boolean classResponseBody = restController || Annotations.has(declaration, "ResponseBody");
            ControllerInfo controller = extractController(type, declaration, classResponseBody, warnings);
            if (!controller.endpoints().isEmpty()) {
                controllers.add(controller);
            }
        }
        return controllers;
    }

    private ControllerInfo extractController(TypeIndex.IndexedType type, ClassOrInterfaceDeclaration declaration,
            boolean classResponseBody, List<Warning> warnings) {
        ParsedUnit unit = type.unit();
        List<String> basePaths = Annotations.find(declaration, "RequestMapping")
                .map(annotation -> paths(annotation, unit, type.qualifiedName(), warnings))
                .orElse(List.of(""));
        Map<String, String> fieldTypes = MemberSupport.fieldTypes(declaration);
        String classSecurity = security(declaration);
        boolean classDeprecated = Annotations.has(declaration, "Deprecated");
        List<EndpointInfo> endpoints = new ArrayList<>();
        Map<String, Integer> idCounts = new HashMap<>();
        for (MethodDeclaration method : declaration.getMethods()) {
            Optional<Mapping> mapping = mapping(method, unit, type.qualifiedName() + "#" + method.getNameAsString(),
                    warnings);
            if (mapping.isEmpty() || !classResponseBody && !Annotations.has(method, "ResponseBody")) {
                continue;
            }
            for (String basePath : basePaths) {
                for (String subPath : mapping.get().paths()) {
                    for (HttpMethod httpMethod : mapping.get().methods()) {
                        String baseId = declaration.getNameAsString() + "#" + method.getNameAsString();
                        int count = idCounts.merge(baseId, 1, Integer::sum);
                        String id = count == 1 ? baseId : baseId + "#" + count;
                        endpoints.add(endpoint(id, httpMethod, joinPaths(contextPath, basePath, subPath), method,
                                mapping.get(), unit, fieldTypes, classSecurity, classDeprecated));
                    }
                }
            }
        }
        List<String> dependencies = fieldTypes.values().stream().distinct().sorted().toList();
        return new ControllerInfo(declaration.getNameAsString(), type.qualifiedName(),
                joinPaths(contextPath, basePaths.get(0)), Javadocs.of(declaration), dependencies, endpoints);
    }

    private EndpointInfo endpoint(String id, HttpMethod httpMethod, String path, MethodDeclaration method,
            Mapping mapping, ParsedUnit unit, Map<String, String> fieldTypes, String classSecurity,
            boolean classDeprecated) {
        List<ParameterInfo> parameters = new ArrayList<>();
        RequestBodyInfo requestBody = null;
        for (Parameter parameter : method.getParameters()) {
            if (Annotations.has(parameter, "RequestBody")) {
                requestBody = new RequestBodyInfo(resolver.resolve(parameter.getType(), unit, Map.of()),
                        Annotations.has(parameter, "Valid", "Validated"));
            } else {
                parameters.addAll(parameterInfos(parameter, unit));
            }
        }
        Optional<AnnotationExpr> operation = Annotations.find(method, "Operation");
        String summary = operation.flatMap(a -> Annotations.attribute(a, "summary"))
                .flatMap(Annotations::stringLiteral).orElse("");
        String description = operation.flatMap(a -> Annotations.attribute(a, "description"))
                .flatMap(Annotations::stringLiteral).filter(text -> !text.isBlank())
                .orElse(Javadocs.of(method));
        String security = security(method);
        return new EndpointInfo(id, httpMethod, path, method.getNameAsString(), parameters, requestBody,
                new ResponseInfo(successStatus(method), responseBody(method.getType(), unit)), List.of(),
                ExceptionScanner.scan(method), security.isEmpty() ? classSecurity : security,
                classDeprecated || Annotations.has(method, "Deprecated"), summary, description,
                mapping.consumes(), mapping.produces(),
                MemberSupport.fieldCalls(method, fieldTypes, serviceNames::contains));
    }

    private List<ParameterInfo> parameterInfos(Parameter parameter, ParsedUnit unit) {
        if (Annotations.has(parameter, SKIPPED_PARAMETER_ANNOTATIONS)) {
            return List.of();
        }
        List<Constraint> constraints = Constraints.from(parameter);
        String typeName = MemberSupport.simpleTypeName(parameter.getType());
        Optional<AnnotationExpr> pathVariable = Annotations.find(parameter, "PathVariable");
        if (pathVariable.isPresent()) {
            return List.of(new ParameterInfo(parameterName(pathVariable.get(), parameter, unit), ParameterLocation.PATH,
                    resolver.resolve(parameter.getType(), unit, Map.of()), true, "", constraints));
        }
        Optional<AnnotationExpr> query = Annotations.find(parameter, "RequestParam");
        Optional<AnnotationExpr> header = Annotations.find(parameter, "RequestHeader");
        if (query.isPresent() || header.isPresent()) {
            if (MAP_TYPES.contains(typeName)) {
                return List.of();
            }
            AnnotationExpr annotation = query.orElseGet(header::get);
            Optional<Expression> defaultAttribute = Annotations.attribute(annotation, "defaultValue");
            Optional<String> defaultValue = defaultAttribute
                    .flatMap(expression -> constants.resolveString(expression, unit));
            // a declared defaultValue makes the parameter optional even when its text cannot be resolved
            boolean required = Annotations.attribute(annotation, "required")
                    .flatMap(Annotations::booleanLiteral).orElse(true) && defaultAttribute.isEmpty();
            return List.of(new ParameterInfo(parameterName(annotation, parameter, unit),
                    query.isPresent() ? ParameterLocation.QUERY : ParameterLocation.HEADER,
                    resolver.resolve(parameter.getType(), unit, Map.of()), required, defaultValue.orElse(""),
                    constraints));
        }
        if (typeName.equals("Pageable")) {
            return List.of(
                    new ParameterInfo("page", ParameterLocation.QUERY, new ScalarType(ScalarKind.INTEGER), false, "0", List.of()),
                    new ParameterInfo("size", ParameterLocation.QUERY, new ScalarType(ScalarKind.INTEGER), false, "20", List.of()),
                    new ParameterInfo("sort", ParameterLocation.QUERY, new ArrayOf(new ScalarType(ScalarKind.STRING)), false, "", List.of()));
        }
        if (FRAMEWORK_PARAMETERS.contains(typeName)) {
            return List.of();
        }
        TypeRef type = resolver.resolve(parameter.getType(), unit, Map.of());
        if (type instanceof OpaqueType || type instanceof MapOf) {
            return List.of();
        }
        return List.of(new ParameterInfo(parameter.getNameAsString(), ParameterLocation.QUERY, type, false, "", constraints));
    }

    private String parameterName(AnnotationExpr annotation, Parameter parameter, ParsedUnit unit) {
        return Annotations.firstAttribute(annotation, "value", "name")
                .flatMap(expression -> constants.resolveString(expression, unit))
                .filter(name -> !name.isBlank())
                .orElse(parameter.getNameAsString());
    }

    private Optional<Mapping> mapping(MethodDeclaration method, ParsedUnit unit, String location, List<Warning> warnings) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = Annotations.simpleName(annotation);
            List<HttpMethod> methods;
            if (SHORTCUT_MAPPINGS.containsKey(name)) {
                methods = List.of(SHORTCUT_MAPPINGS.get(name));
            } else if (name.equals("RequestMapping")) {
                methods = Annotations.attribute(annotation, "method")
                        .map(value -> Annotations.elements(value).stream()
                                .map(Annotations::lastIdentifier)
                                .map(ControllerExtractor::httpMethod)
                                .flatMap(Optional::stream)
                                .toList())
                        .filter(list -> !list.isEmpty())
                        .orElse(List.of(HttpMethod.ANY));
            } else {
                continue;
            }
            return Optional.of(new Mapping(methods, paths(annotation, unit, location, warnings),
                    mediaTypes(annotation, "consumes", unit), mediaTypes(annotation, "produces", unit)));
        }
        return Optional.empty();
    }

    private static Optional<HttpMethod> httpMethod(String name) {
        try {
            return Optional.of(HttpMethod.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unsupported) {
            return Optional.empty();
        }
    }

    private List<String> paths(AnnotationExpr annotation, ParsedUnit unit, String location, List<Warning> warnings) {
        Optional<Expression> value = Annotations.firstAttribute(annotation, "value", "path");
        if (value.isEmpty()) {
            return List.of("");
        }
        List<String> paths = new ArrayList<>();
        for (Expression element : Annotations.elements(value.get())) {
            Optional<String> resolved = constants.resolveString(element, unit);
            if (resolved.isPresent()) {
                paths.add(resolved.get());
            } else {
                warnings.add(new Warning("UNRESOLVED_PATH", "Could not evaluate path expression " + element, location));
                paths.add("[" + element + "]");
            }
        }
        return paths.isEmpty() ? List.of("") : paths;
    }

    private List<String> mediaTypes(AnnotationExpr annotation, String attribute, ParsedUnit unit) {
        return Annotations.attribute(annotation, attribute)
                .map(value -> Annotations.elements(value).stream()
                        .map(element -> constants.resolveString(element, unit)
                                .orElseGet(() -> MEDIA_TYPES.getOrDefault(Annotations.lastIdentifier(element),
                                        element.toString())))
                        .distinct()
                        .toList())
                .orElse(List.of());
    }

    private TypeRef responseBody(Type type, ParsedUnit unit) {
        if (type.isVoidType()) {
            return null;
        }
        if (type instanceof ClassOrInterfaceType classType) {
            String simple = classType.getNameAsString();
            if (simple.equals("Void")) {
                return null;
            }
            if (RESPONSE_WRAPPERS.contains(simple)) {
                List<Type> arguments = classType.getTypeArguments().map(args -> List.<Type>copyOf(args)).orElse(List.of());
                if (arguments.isEmpty()) {
                    return new OpaqueType(simple);
                }
                if (arguments.get(0) instanceof WildcardType wildcard && wildcard.getExtendedType().isEmpty()) {
                    return new OpaqueType("Object");
                }
                return responseBody(arguments.get(0), unit);
            }
        }
        return resolver.resolve(type, unit, Map.of());
    }

    private static int successStatus(MethodDeclaration method) {
        OptionalInt declared = StatusHeuristics.responseStatusCode(method);
        if (declared.isPresent()) {
            return declared.getAsInt();
        }
        return method.getBody()
                .flatMap(body -> StatusHeuristics.responseEntityStatuses(body).stream()
                        .filter(status -> status >= 200 && status < 300)
                        .findFirst())
                .orElse(200);
    }

    private static String security(NodeWithAnnotations<?> node) {
        Optional<AnnotationExpr> preAuthorize = Annotations.find(node, "PreAuthorize");
        if (preAuthorize.isPresent()) {
            return Annotations.attribute(preAuthorize.get(), "value").flatMap(Annotations::stringLiteral).orElse("");
        }
        return Annotations.find(node, "Secured", "RolesAllowed")
                .flatMap(roles -> Annotations.attribute(roles, "value"))
                .map(value -> "roles: " + Annotations.elements(value).stream()
                        .map(Annotations::valueText)
                        .collect(Collectors.joining(", ")))
                .orElse("");
    }

    /** Joins path fragments with single slashes; the empty path is "/". */
    public static String joinPaths(String... parts) {
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            for (String segment : part.split("/")) {
                if (!segment.isBlank()) {
                    path.append('/').append(segment.strip());
                }
            }
        }
        return path.isEmpty() ? "/" : path.toString();
    }
}
