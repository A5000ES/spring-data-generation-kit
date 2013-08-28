package com.redshape.generators.jpa.entities;

import com.redshape.generators.annotations.ConventionalQueries;
import com.redshape.generators.annotations.ConventionalQuery;
import com.redshape.generators.annotations.Parameter;
import com.redshape.generators.annotations.dto.DtoExtend;
import com.redshape.generators.annotations.dto.DtoMethod;

@javax.persistence.Entity
@ConventionalQueries({
    @ConventionalQuery(
        name = "findByName",
        isCollection = true,
        isPageable = true,
        parameters = {
            @Parameter( value = "name", type = String.class )
        }
    )
})
@DtoExtend({
    @Parameter( value = "testParentId", type = Long.class )
})
public class Test extends TestParent {

    private String name;

    private Test relatedTest;

    @DtoMethod
    public Test getRelatedTestX() {
        return relatedTest;
    }

    @DtoMethod
    public static int add( int y, int z ) {
        return y + z;
    }

}