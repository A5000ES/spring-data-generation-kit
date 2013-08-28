package com.redshape.generators.jpa.mojo;

import com.redshape.generators.jpa.AbstractGeneratorMojo;
import com.thoughtworks.qdox.model.JavaClass;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Created by cyril on 8/28/13.
 */
@Mojo( name = "gen-dto-converter" )
public class GenDtoToJpaConverterMojo extends AbstractGeneratorMojo {

    public GenDtoToJpaConverterMojo() {
        super("DTO to JPA conversion services generator");
    }

    @Override
    protected void generateClass(JavaClass className) throws MojoExecutionException {

    }

    @Override
    protected boolean isSupported(JavaClass entityClass) {
        return isJpaEntity(entityClass);
    }
}
