package com.redshape.generators.annotations.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created with IntelliJ IDEA.
 * User: cyril
 * Date: 11/20/12
 * Time: 3:15 PM
 * To change this template use File | Settings | File Templates.
 */
@Target(ElementType.FIELD)
public @interface TargetGroup {

    /**
     * @return
     */
    public String value();

    /**
     * If value provided, then marked field and its value would be
     * included/excluded in/from resulted DTO structure when profile referenced
     * by {@link TargetGroup#value()} would be requested to be
     * be generated
     *
     * @return
     */
    public boolean includes() default true;

}
