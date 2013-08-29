package com.redshape.generators.jpa.entities;

import com.redshape.generators.annotations.*;
import com.redshape.generators.annotations.dto.AggregationType;
import com.redshape.generators.annotations.dto.DtoExtend;
import com.redshape.generators.annotations.dto.DtoInclude;
import com.redshape.generators.annotations.dto.DtoMethod;

import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.List;

@javax.persistence.Entity
@ConventionalQueries({
    @ConventionalQuery(
        name = "findByName",
        isCollection = false,
        isPageable = true,
        parameters = {
            @Parameter( value = "name", type = String.class )
        }
    ),
    @ConventionalQuery(
        name = "findByUserId",
        parameters = {
                @Parameter( value = "userId", type = Long.class )
        }
    )
})
@NativeQueries({
    @NativeQuery(
        name = "deleteWhereUserIdIs",
        isModifying = true,
        isTransactional = true,
        value = "delete from Test where id = :id",
        parameters = {
            @Parameter( value = "id", type = Long.class )
        }
    ),
    @NativeQuery(
        name = "deleteWhereUserIdIn",
        isModifying = true,
        isTransactional = true,
        resultType = void.class,
        value = "delete from Test where id in (:id)",
        parameters = {
                @Parameter( value = "id", type = Long.class, isArray = true)
        }
    ),
    @NativeQuery(
        name = "countById",
        value = "select count(id) from Test where :id",
        resultType = Long.class,
        parameters = {
            @Parameter( value = "id", type = Long.class )
        }
    )
})
@DtoExtend({
    @Parameter( value = "testParentId", type = Long.class )
})
public class Test extends TestParent {

    private String name;

    @DtoInclude(AggregationType.DTO)
    private Test relatedTest;

    @DtoInclude(AggregationType.ID)
    private Test relatedTestA;

    @OneToMany( targetEntity = Test.class )
    @DtoInclude(AggregationType.DTO)
    private List<Test> relatedTests;

    @ManyToOne( targetEntity = Test.class )
    @DtoInclude(AggregationType.DTO)
    private ITest relatedTestByInterface;

    @DtoMethod
    public Test getRelatedTestX() {
        return relatedTest;
    }

    @DtoMethod
    public static int add( int y, int z ) {
        return y + z;
    }

}