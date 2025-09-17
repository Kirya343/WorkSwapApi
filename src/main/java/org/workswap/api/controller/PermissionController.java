package org.workswap.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.permission.PermissionDTO;
import org.workswap.common.dto.permission.RoleDTO;
import org.workswap.core.services.command.PermissionCommandSevice;
import org.workswap.core.services.command.UserCommandService;
import org.workswap.core.services.query.PermissionQueryService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.datasource.central.model.User;
import org.workswap.datasource.central.model.user.Permission;
import org.workswap.datasource.central.model.user.Role;
import org.workswap.datasource.central.repository.PermissionRepository;
import org.workswap.datasource.central.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/permissions")
public class PermissionController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final PermissionQueryService permissionQueryService;
    private final PermissionCommandSevice permissionCommandSevice;

    @GetMapping
    public ResponseEntity<?> getPermissions() {

        List<PermissionDTO> perms = permissionQueryService.getAllPermissionDtos();

        return ResponseEntity.ok(Map.of("permissions", perms));
    }

    @GetMapping("/roles")
    public ResponseEntity<?> getRoles() {

        List<RoleDTO> roles = permissionQueryService.getAllRoleDtos();

        return ResponseEntity.ok(Map.of("roles", roles));
    }

    @GetMapping("/{id}/get")
    public ResponseEntity<?> getPermissionsByRole(@PathVariable Long id) {

        Role role = permissionQueryService.findRole(id.toString());

        List<PermissionDTO> perms = permissionQueryService.getPermissionDtosByRole(role);

        return ResponseEntity.ok(Map.of("permissions", perms));
    }

    //пометить пермишном
    @GetMapping("/{id}")
    public ResponseEntity<?> getPermisstinsByRole(@PathVariable Long id) {
        
        Role role = roleRepository.findById(id).orElse(null);
        Set<Permission> perms = role.getPermissions();

        List<Permission> permsList = new ArrayList<>(perms);

        return ResponseEntity.ok().body(permsList);
    }

    //пометить пермишном
    @PutMapping("/{roleId}/save")
    public ResponseEntity<?> savePermissionsForRole(
        @PathVariable Long roleId,
        @RequestBody List<PermissionDTO> permissions
    ) {

        Role role = permissionQueryService.findRole(roleId.toString());

        permissionCommandSevice.updateRolePermissions(role, permissions);

        return ResponseEntity.ok(Map.of("message", "Разрешения для роли сохранены"));
    }

    @PreAuthorize("hasAuthority('CREATE_ROLES')")
    @PostMapping("/create/role")
    public ResponseEntity<?> createRole(@RequestParam String roleName) {
        Role role = new Role(roleName);
        roleRepository.save(role);

        return ResponseEntity.ok(Map.of("message", "Роль создана"));
    }

    @PreAuthorize("hasAuthority('CREATE_PERMISSIONS')")
    @PostMapping("/create/permission")
    public ResponseEntity<?> createPermission(@RequestParam String permissionName) {
        Permission perm = new Permission(permissionName);
        permissionRepository.save(perm);

        return ResponseEntity.ok(Map.of("message", "Разрешение создано"));
    }

    @PreAuthorize("hasAuthority('ADD_USER_ROLE')")
    @PostMapping("/user/role/add")
    public ResponseEntity<?> userAddRoles(@RequestParam Long userId, @RequestParam Long role) {
        
        User user = userQueryService.findUser(userId.toString());
        user.getRoles().add(roleRepository.findById(role).orElse(null));

        userCommandService.save(user);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('REMOVE_USER_ROLE')")
    @PostMapping("/user/role/remove")
    public ResponseEntity<?> userRemoveRole(@RequestParam Long userId, @RequestParam Long role) {
        
        User user = userQueryService.findUser(userId.toString());
        user.getRoles().remove(roleRepository.findById(role).orElse(null));

        userCommandService.save(user);

        return ResponseEntity.ok().build();
    }
}