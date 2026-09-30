package dev.apidocs.core.context;

import dev.apidocs.core.model.ServiceMethod;

public record ServiceMethodRef(String service, ServiceMethod method) {
}
