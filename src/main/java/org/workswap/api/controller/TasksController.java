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
import org.workswap.common.dto.TaskCommentDTO;
import org.workswap.common.dto.TaskDTO;
import org.workswap.common.dto.UserDTO;
import org.workswap.common.enums.TaskStatus;
import org.workswap.common.enums.TaskType;
import org.workswap.core.services.TaskService;
import org.workswap.core.services.UserService;
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
    private final UserService userService;
    private final TaskService taskService;
    private final TaskCommentRepository taskCommentRepository;

    @PreAuthorize("hasAuthority('CREATE_TASK')")
    @PostMapping("/create")
    public ResponseEntity<?> createTask(@RequestParam String taskName,
                                @RequestParam String taskDescription,
                                @RequestParam String taskType,
                                @RequestParam LocalDateTime deadline, 
                                @RequestHeader("X-User-Sub") String userSub) {
        Task task = new Task(taskName, 
                             taskDescription, 
                             deadline, 
                             TaskType.valueOf(taskType), 
                             userService.findUser(userSub).getId());

        taskRepository.save(task);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('UPDATE_TASK')")
    @PostMapping("/{id}/update")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('PICKUP_TASK')")
    @PostMapping("/pickup")
    public ResponseEntity<?> pickupTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());

        task.setExecutorId(userService.findUser(userSub).getId());
        task.setStatus(TaskStatus.IN_PROGRESS);

        taskRepository.save(task);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('COMPLETE_TASK')")
    @PostMapping("/complete")
    public ResponseEntity<?> completeTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());
        User user = userService.findUser(userSub);

        if (user.getId() != task.getExecutorId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы можете завершать только свои задачи!");
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompleted(LocalDateTime.now());

        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('CANCEL_TASK')")
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelTask(@RequestParam Long taskId, @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(taskId.toString());
        
        task.setStatus(TaskStatus.CANCELED);

        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('DELETE_TASK')")
    @PostMapping("/{id}/delete")
    public ResponseEntity<?> deleteTask(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('CREATE_TASK_COMMENT')")
    @PostMapping("/{id}/comment")
    public ResponseEntity<?> commentToTask(@PathVariable Long id,
                                            @RequestParam String commentContent, 
                                            @RequestHeader("X-User-Sub") String userSub) {
        Task task = taskService.findTask(id.toString());

        TaskComment comment = new TaskComment(commentContent, userService.findUser(userSub).getId(), task);

        taskCommentRepository.save(comment);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('DELETE_TASK_COMMENT')")
    @PostMapping("/comment/delete")
    public ResponseEntity<?> deleteCommentToTask(@RequestParam Long id, 
                                                @RequestHeader("X-User-Sub") String userSub) {

        TaskComment comment = taskCommentRepository.findById(id).orElse(null);
        
        if (comment.getAuthorId() != userService.findUser(userSub).getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы можете удалять только свои комментарии!");
        }

        taskCommentRepository.delete(comment);
        return ResponseEntity.ok().build();
    }

    //пометить пермишном
    @GetMapping("/metadata")
    public ResponseEntity<?> getTaskSettings() {

        TaskType[] taskTypeList = TaskType.values();
        TaskStatus[] taskStatusList = TaskStatus.values();

        return ResponseEntity.ok().body(Map.of("taskStatusList", taskStatusList, "taskTypeList", taskTypeList));
    }

    //пометить пермишном
    @GetMapping("/get-tasks")
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

    @PreAuthorize("hasAuthority('VIEW_TASK_DETAILS')")
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getTaskDetailsFragment(@PathVariable Long id) {
        TaskDTO task = taskService.convertToDto(taskService.findTask(id.toString()));

        UserDTO executor = null;
        if(task.getExecutorId() != null) {
            executor = userService.convertToDto(userService.findUser(task.getExecutorId().toString()));
        }

        UserDTO author = userService.convertToDto(userService.findUser(task.getAuthorId().toString()));

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

    @PreAuthorize("hasAuthority('VIEW_TASK_COMMENTS')")
    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getTaskComments(@PathVariable Long id) {
        List<TaskCommentDTO> comments = taskCommentRepository.findAllByTaskId(id)
                                                             .stream()
                                                             .map(comment -> taskService.convertCommentToDto(comment))
                                                             .toList();
        
        return ResponseEntity.ok().body(Map.of("comments", comments));
    }
}