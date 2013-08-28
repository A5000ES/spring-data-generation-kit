package com.redshape.generators.jpa.v2;

import com.redshape.generators.jpa.mojo.GenDaoMojo;
import com.redshape.generators.jpa.mojo.GenDtoMojo;
import com.redshape.generators.jpa.utils.Commons;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Created by cyril on 8/28/13.
 */
public class GenDaoMojoTest extends AbstractMojoTestCase {

    /**
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * @throws Exception
     */
    public void testGenDaoGoal() throws Exception
    {
        Xpp3Dom configuration = createConfiguration();
        MavenProject project = createProject( configuration );

        MojoExecution mojoExecution = newMojoExecution("gen-dao");
        mojoExecution.setConfiguration( configuration );

        GenDaoMojo mojo = (GenDaoMojo) lookupConfiguredMojo(newMavenSession(project), mojoExecution);
        mojo.setProject(project);
        mojo.execute();
        assertNotNull( mojo );
    }

    public void testGenDtoGoal() throws Exception {
        Xpp3Dom configuration = createConfiguration();
        MavenProject project = createProject( configuration );

        MojoExecution mojoExecution = newMojoExecution("gen-dto");
        mojoExecution.setConfiguration( configuration );

        GenDtoMojo mojo = (GenDtoMojo) lookupConfiguredMojo(newMavenSession(project), mojoExecution);
        mojo.setProject(project);
        mojo.execute();
        assertNotNull( mojo );
    }

    protected Xpp3Dom createConfiguration() {
        Xpp3Dom configuration = new Xpp3Dom("configuration");

        Xpp3Dom sourceRootNode = new Xpp3Dom("sourceRoot");
        sourceRootNode.setValue("src/test/java");
        configuration.addChild( sourceRootNode );

        Xpp3Dom entityPatternNode = new Xpp3Dom("entityPattern");
        entityPatternNode.setValue("**/*.java");
        configuration.addChild(entityPatternNode);

        return configuration;
    }

    protected MavenProject createProject( Xpp3Dom configuration ) {
        MavenProjectStub project = new MavenProjectStub();
        project.setGroupId("test-mojo");
        project.setArtifactId("test-mojo");
        project.setCompileSourceRoots(Commons.list("src/test/java"));

        Plugin generatorPlugin = new Plugin();
        generatorPlugin.setGroupId("com.redshape.utils.generation-kit");
        generatorPlugin.setArtifactId("spring-data-generator");
        generatorPlugin.setVersion("1.0.23-SNAPSHOT");
        generatorPlugin.setConfiguration( configuration );

        Build build = new Build();
        build.addPlugin(generatorPlugin);
        project.setBuild(build);

        return project;
    }

}
