package com.example.claims.user;

import com.example.claims.common.web.ApiExceptions.UnauthenticatedException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Stand-in for real authentication: the X-User header names a seeded user, and the
 * role is looked up in the DB. A role sent by the client is never trusted.
 * Swapping in real auth (e.g. JWT) only means changing this one class.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-User";

    private final AppUserRepository users;

    public CurrentUserArgumentResolver(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String username = request.getHeader(HEADER);
        if (username == null || username.isBlank()) {
            throw new UnauthenticatedException("Missing " + HEADER + " header");
        }
        return users.findByUsername(username.trim())
                .map(CurrentUser::from)
                .orElseThrow(() -> new UnauthenticatedException("Unknown user"));
    }
}
