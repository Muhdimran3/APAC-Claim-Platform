package com.example.claims.config;

import com.example.claims.user.CurrentUser;
import com.example.claims.user.CurrentUserArgumentResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documents X-User as an API-key header so Swagger UI's "Authorize" button sets it for every call. */
@Configuration
public class OpenApiConfig {

    static {
        // CurrentUser is resolved from the header, not from request params, so hide it from the docs.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI claimsOpenApi() {
        String scheme = CurrentUserArgumentResolver.HEADER;
        return new OpenAPI()
                .info(new Info()
                        .title("Claims Platform API")
                        .version("v1")
                        .description("Local demo. Authorize with a seeded username: "
                                + "alice, ben (claimants); olivia, oscar (officers); maya (manager)."))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(CurrentUserArgumentResolver.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
