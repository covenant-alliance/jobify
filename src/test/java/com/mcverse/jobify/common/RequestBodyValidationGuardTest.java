package com.mcverse.jobify.common;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the rule from CLAUDE.md: every request body is validated. Fails when a new endpoint forgets @Valid. */
@SpringBootTest
class RequestBodyValidationGuardTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyRequestBodyParameterIsValidated() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            if (!handler.getBeanType().getPackageName().startsWith("com.mcverse.jobify")) {
                continue;
            }
            for (MethodParameter parameter : handler.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(RequestBody.class)) {
                    checked++;
                    if (!parameter.hasParameterAnnotation(Valid.class)
                            && !parameter.hasParameterAnnotation(Validated.class)) {
                        missing.add(handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName());
                    }
                }
            }
        }
        assertTrue(checked >= 20, "expected to find the request bodies, found " + checked);
        assertTrue(missing.isEmpty(), "These endpoints take a @RequestBody without @Valid: " + missing);
    }
}
