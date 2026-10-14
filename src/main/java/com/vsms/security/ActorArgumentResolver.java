package com.vsms.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controller methods simply declare an {@link Actor} parameter. */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final ActorResolver actorResolver;

    public ActorArgumentResolver(ActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return actorResolver.resolve(SecurityContextHolder.getContext().getAuthentication());
    }
}
