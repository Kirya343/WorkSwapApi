package org.workswap.api.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
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
import org.workswap.core.services.UserService;
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

    private final UserService userService;

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
            @RequestBody List<String> permissionNames) {
        
        try {
            // Валидация входных данных
            if (permissionNames == null) {
                return ResponseEntity.badRequest()
                    .body("Список разрешений не может быть null");
            }

            // Поиск роли
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role == null) {
                return ResponseEntity.notFound().build();
            }

            // Парсинг и валидация разрешений
            Set<Permission> newPermissions = new HashSet<>();
            
            for (String permName : permissionNames) {
                if (permName == null || permName.trim().isEmpty()) {
                    continue; // Пропускаем пустые имена
                }
                
                Permission permission = permissionRepository.findByName(permName.trim());
                if (permission == null) {
                    return ResponseEntity.badRequest()
                        .body("Разрешение '" + permName + "' не найдено");
                }
                newPermissions.add(permission);
            }

            role.setPermissions(newPermissions);
            roleRepository.save(role);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            System.err.println("Ошибка при сохранении разрешений: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Внутренняя ошибка сервера");
        }
    }

    @PreAuthorize("hasAuthority('CREATE_ROLES')")
    @PostMapping("/create/role")
    public ResponseEntity<?> createRole(@RequestParam String roleName) {
        Role role = new Role(roleName);
        roleRepository.save(role);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('CREATE_PERMISSIONS')")
    @PostMapping("/create/permission")
    public ResponseEntity<?> createPermission(@RequestParam String permissionName) {
        Permission perm = new Permission(permissionName);
        permissionRepository.save(perm);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ADD_USER_ROLE')")
    @PostMapping("/user/role/add")
    public ResponseEntity<?> userAddRoles(@RequestParam Long userId, @RequestParam Long role) {
        
        User user = userService.findUser(userId.toString());
        user.getRoles().add(roleRepository.findById(role).orElse(null));

        userService.save(user);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('REMOVE_USER_ROLE')")
    @PostMapping("/user/role/remove")
    public ResponseEntity<?> userRemoveRole(@RequestParam Long userId, @RequestParam Long role) {
        
        User user = userService.findUser(userId.toString());
        user.getRoles().remove(roleRepository.findById(role).orElse(null));

        userService.save(user);

        return ResponseEntity.ok().build();
    }
}