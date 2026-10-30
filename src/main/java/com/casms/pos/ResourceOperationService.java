package com.casms.pos;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.casms.pos.PosPermission.ResourceType;

/** Source policies are evaluated live; no grants are copied into global employee roles. */
@Service @RequiredArgsConstructor @Transactional
public class ResourceOperationService {
    private final JdbcTemplate jdbc;

    public List<PosDtos.PermissionDefinition> definitions(ResourceType type) {
        return Arrays.stream(PosPermission.values()).filter(p->type==null || p.resourceType()==type)
                .map(p->new PosDtos.PermissionDefinition(p,p.resourceType(),p.category())).toList();
    }
    public PosDtos.ResourcePolicy get(ResourceType type,UUID id) {
        exists(type,id);
        var rows=jdbc.query("SELECT permissions FROM "+table(type)+" WHERE "+key(type)+"=?",
                (r,n)->PosRepository.permissions(r,"permissions"),id);
        return new PosDtos.ResourcePolicy(id,type,!rows.isEmpty(),rows.isEmpty()?Set.of():Set.copyOf(rows.getFirst()));
    }
    public PosDtos.ResourcePolicy put(ResourceType type,UUID id,Set<PosPermission> permissions,UUID actor) {
        exists(type,id);validate(type,permissions,null);
        jdbc.update("INSERT INTO "+table(type)+" ("+key(type)+",permissions,updated_by_user_id) VALUES (?,string_to_array(?,','),?) "
                +"ON CONFLICT ("+key(type)+") DO UPDATE SET permissions=excluded.permissions,updated_by_user_id=excluded.updated_by_user_id,updated_at=now()",
                id,PosRepository.csv(permissions),actor);
        return get(type,id);
    }
    static void validate(ResourceType type,Set<PosPermission> permissions,Set<PosPermission> parent) {
        if(permissions==null)return; // null is inheritance only on area/device restriction APIs
        if(!PosPermission.forResource(type).containsAll(permissions))
            throw new BusinessException("OPERATION_PERMISSION_TYPE_MISMATCH","Operation permissions must belong to "+type);
        if(parent!=null && !parent.containsAll(permissions))
            throw new BusinessException("POS_PERMISSION_OUTSIDE_PARENT_SCOPE","Restrictions cannot grant operations outside their source policy");
    }
    private void exists(ResourceType type,UUID id) {
        String source=type==ResourceType.SERVICE?"\"Service\"":"\"OnboardActivity\"";
        String column=type==ResourceType.SERVICE?"service_id":"onboard_activity_id";
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+source+" WHERE "+column+"=?)",Boolean.class,id)))
            throw new ResourceNotFoundException(type.name(),id);
    }
    private static String table(ResourceType type) {return type==ResourceType.SERVICE?"\"ServiceOperationPolicy\"":"\"ActivityOperationPolicy\"";}
    private static String key(ResourceType type) {return type==ResourceType.SERVICE?"service_id":"activity_id";}
}
