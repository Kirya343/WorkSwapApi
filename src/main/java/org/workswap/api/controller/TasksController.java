package org.workswap.api.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.dto.task.TaskCommentDTO;
import org.workswap.common.dto.task.TaskDTO;
import org.workswap.common.dto.user.UserDTO;
import org.workswap.common.enums.TaskStatus;
import org.workswap.common.enums.TaskType;
import org.workswap.core.services.TaskService;
import org.workswap.core.services.mapping.UserMappingService;
import org.workswap.core.services.query.UserQueryService;
import org.workswap.datasource.admin.model.Task;
import org.workswap.datasource.admin.model.TaskComment;
import org.workswap.datasource.admin.repository.TaskCommentRepository;
import org.workswap.datasource.admin.repository.TaskRepository;
import org.workswap.datasource.central.model.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TasksController {
    
    private final TaskRepository taskRepository;
    private final UserQueryService userQueryService;
    private final UserMappingService userMappingService;
    private final TaskService taskService;
    private final TaskCommentRepository taskCommentRepository;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_TASK')")
    public ResponseEntity<?> createTask(@RequestParam String taskName,
                                @RequestParam String taskDescription,
                                @RequestParam String taskType,
                                @RequestParam LocalDateTime deadline, 
                                @RequestHeader("X-User-Sub") String userSub) {
        Task task = new Task(taskName, 
                             taskDescription, 
                             deadline, 
                             TaskType.valueOf(taskType), 
                             userQueryService.findUser(userSub).getId());

        taskRepository.save(task);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/update")
    @PreAuthorize("hasAuthority('UPDATE_TASK')")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pickup")
    @PreAuthorize("hasAuthority('PICKUP_TASK')")
    public ResponseEntity<?> pickupTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());

        task.setExecutorId(userQueryService.findUser(userSub).getId());
        task.setStatus(TaskStatus.IN_PROGRESS);

        taskRepository.save(task);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('COMPLETE_TASK')")
    public ResponseEntity<?> completeTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());
        User user = userQueryService.findUser(userSub);

        if (user.getId() != task.getExecutorId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы можете завершать только свои задачи!");
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompleted(LocalDateTime.now());

        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('CANCEL_TASK')")
    public ResponseEntity<?> cancelTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());
        
        task.setStatus(TaskStatus.CANCELED);

        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_TASK')")
    public ResponseEntity<?> deleteTask(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comment")
    @PreAuthorize("hasAuthority('CREATE_TASK_COMMENT')")
    public ResponseEntity<?> commentToTask(@PathVariable Long id,
                                            @RequestParam String commentContent, 
                                            @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(id.toString());

        TaskComment comment = new TaskComment(commentContent, userQueryService.findUser(userSub).getId(), task);

        taskCommentRepository.save(comment);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/comment/delete")
    @PreAuthorize("hasAuthority('DELETE_TASK_COMMENT')")
    public ResponseEntity<?> deleteCommentToTask(@RequestParam Long id, 
                                                @RequestHeader("X-User-Sub") String userSub) {

        TaskComment comment = taskCommentRepository.findById(id).orElse(null);
        
        if (comment.getAuthorId() != userQueryService.findUser(userSub).getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы можете удалять только свои комментарии!");
        }

        taskCommentRepository.delete(comment);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('GET_TASK_METADATA')")
    public ResponseEntity<?> getTaskSettings() {

        TaskType[] taskTypeList = TaskType.values();
        TaskStatus[] taskStatusList = TaskStatus.values();

        return ResponseEntity.ok().body(Map.of("taskStatusList", taskStatusList, "taskTypeList", taskTypeList));
    }

    @GetMapping("/get-tasks")
    @PreAuthorize("hasAuthority('GET_TASKS')")
    public ResponseEntity<?> getTasksTable(
        @RequestParam(required = false, defaultValue = "created") String sort, 
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        
        List<TaskDTO> tasks = taskService.getSortedTasks(sort, type, status)
                                         .stream()
                                         .map(task -> taskService.convertToShortDto(task))
                                         .toList();

        return ResponseEntity.ok(Map.of("tasks", tasks));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('VIEW_TASK_DETAILS')")
    public ResponseEntity<?> getTaskDetailsFragment(@PathVariable Long id) {
        TaskDTO task = taskService.convertToDto(taskService.findTask(id.toString()));

        UserDTO executor = null;
        if(task.getExecutorId() != null) {
            executor = userMappingService.toDto(userQueryService.findUser(task.getExecutorId().toString()));
        }

        UserDTO author = userMappingService.toDto(userQueryService.findUser(task.getAuthorId().toString()));

        Map<String, Object> response = new HashMap<>();

        response.put("task", task);
        if (executor != null) {
            response.put("executor", executor);
        }
        if (author != null) {
            response.put("author", author);
        }

        return ResponseEntity.ok().body(response); // путь и имя фрагмента
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('VIEW_TASK_COMMENTS')")
    public ResponseEntity<?> getTaskComments(@PathVariable Long id) {
        List<TaskCommentDTO> comments = taskCommentRepository.findAllByTaskId(id)
                                                             .stream()
                                                             .map(comment -> taskService.convertCommentToDto(comment))
                                                             .toList();
        
        return ResponseEntity.ok().body(Map.of("comments", comments));
    }
}