package com.vsms.config;

import com.vsms.security.Actor;
import com.vsms.security.ActorResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Values every page needs: who is signed in and the business details. */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final ActorResolver actorResolver;
    private final VsmsProperties properties;

    public GlobalModelAttributes(ActorResolver actorResolver, VsmsProperties properties) {
        this.actorResolver = actorResolver;
        this.properties = properties;
    }

    @ModelAttribute("currentUser")
    public Actor currentUser(Authentication authentication) {
        return actorResolver.resolve(authentication);
    }

    @ModelAttribute("business")
    public VsmsProperties.Business business() {
        return properties.business();
    }
}
