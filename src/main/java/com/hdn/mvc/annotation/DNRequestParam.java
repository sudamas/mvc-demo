package com.hdn.mvc.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DNRequestParam {
    
    String value() default "";
    boolean require() default true;
}
