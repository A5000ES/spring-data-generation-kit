package com.redshape.generators.annotations.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by cyril on 8/28/13.
 */
@Target(ElementType.METHOD)
public @interface DtoMethod {

    public boolean isAbstract() default true;

}
