package org.workswap.api.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.workswap.core.services.LocalizationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lang")
public class ApiLocalizationController {

    @Value("${api.key.localization}")
    private String localizationApiKey;

    private final Path baseLocalizationDir = Path.of("localization");

    private final LocalizationService localizationService;

    @GetMapping("/files/{locale}")
    public ResponseEntity<List<String>> listLocaleFiles(@PathVariable String locale, @RequestHeader("X-API-KEY") String apiKey) throws IOException {
        if (!localizationApiKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        if (!Files.exists(baseLocalizationDir)) {
            return ResponseEntity.notFound().build();
        }

        List<String> filePaths = new ArrayList<>();

        try (Stream<Path> folderStream = Files.walk(baseLocalizationDir, 1)) {
            folderStream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(baseLocalizationDir)) // исключаем корневой каталог
                    .forEach(folder -> {
                        try (Stream<Path> files = Files.list(folder)) {
                            files.filter(Files::isRegularFile)
                                .filter(p -> p.getFileName().toString().endsWith("_" + locale + ".properties"))
                                .forEach(file -> {
                                    Path relativeFolder = baseLocalizationDir.relativize(folder);
                                    String relativePath = relativeFolder.resolve(file.getFileName()).toString().replace("\\", "/");
                                    filePaths.add(relativePath);
                                });
                        } catch (IOException e) {
                            System.err.println("Ошибка при чтении папки: " + folder + " — " + e.getMessage());
                        }
                    });
        }

        return ResponseEntity.ok(filePaths);
    }

    @GetMapping("/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> getLocalizationFile(
            @PathVariable String folder,
            @PathVariable String fileName
    ) throws IOException {
        Path folderPath = baseLocalizationDir.resolve(folder);
        Path filePath = folderPath.resolve(fileName);

        // Защита от Path Traversal атак
        if (!filePath.normalize().startsWith(baseLocalizationDir.normalize())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(filePath));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + fileName)
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @PreAuthorize("hasAuthority('CREATE_LOCALIZATION_POINT')")
    @PostMapping("/create")
    public ResponseEntity<?> createLocalizationPoint(@RequestParam String code,
                                                    @RequestParam String group,
                                                    @RequestParam("translations") String translationsRaw
        ) throws IOException {

        System.out.println(translationsRaw);

        List<String> translations = Arrays.asList(translationsRaw.split("§"));

        if (translations != null) {
            for (String translation : translations) {
                String[] parts = translation.split("\\¤");

                if (parts.length == 2) {
                    String text = parts[0];   // "перевод"
                    String lang = parts[1];   // "ru"
                    localizationService.createTranslation("localization/" + group + "/" + group, code, lang, text);
                } else {
                    // Обработка ошибки: неверный формат
                    throw new IllegalArgumentException("Неверный формат строки. Ожидалось: текст.язык");
                }
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Точка локализации успешно создана"));
    }

    @PreAuthorize("hasAuthority('CREATE_LOCALIZATION_GROUP')")
    @PostMapping("/create-group")
    public ResponseEntity<?> createLocalizationGroup(@RequestParam String group, RedirectAttributes redirectAttributes) {
        try {
            Path path = Paths.get("localization/" + group);
            Files.createDirectory(path);

            return ResponseEntity.ok(Map.of("success", true,
                                            "message", "Группа локализации успешно создана"));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при удалении: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LOCALIZATION_GROUP')")
    @PostMapping("/delete-group")
    public ResponseEntity<?> deleteLocalizationGroup(@RequestParam String group, RedirectAttributes redirectAttributes) {
        try {
            Path dir = Paths.get("localization/" + group);
            Files.walk(dir)
                .sorted(Comparator.reverseOrder()) // Сначала файлы, потом папки
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            return ResponseEntity.ok(Map.of("success", true, "message", "Группа локализации успешно удалена"));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при удалении: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LOCALIZATION_POINT')")
    @PostMapping("/delete")
    public ResponseEntity<?> deleteLocalizationPoint(@RequestParam String code, RedirectAttributes redirectAttributes) {
        try {
            localizationService.removeTranslation("localization", code);

            return ResponseEntity.ok(Map.of("success", true, "message", "Точка локализации успешно удалена"));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при удалении: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('CREATE_LOCALIZATION_POINT')")
    @PostMapping("/create-translation")
    @ResponseBody
    public ResponseEntity<?> createLocalizationPointFetch(@RequestParam String code,
                                                          @RequestParam String group,
                                                          @RequestParam("translations") String translationsRaw) {
        try {
            List<String> translations = Arrays.asList(translationsRaw.split("§"));

            for (String translation : translations) {
                String[] parts = translation.split("\\¤");

                if (parts.length == 2) {
                    String text = parts[0];   // перевод
                    String lang = parts[1];   // ru, en и т.д.
                    localizationService.createTranslation("localization/" + group + "/" + group, code, lang, text);
                } else {
                    return ResponseEntity
                        .badRequest()
                        .body(Map.of("success", false, "message", "Неверный формат: " + translation));
                }
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "Точка локализации успешно создана"));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при создании: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('DELETE_LOCALIZATION_POINT')")
    @DeleteMapping("/delete-translation")
    @ResponseBody
    public ResponseEntity<?> deleteLocalizationPointFetch(@RequestParam String code) {
        try {
            localizationService.removeTranslation("localization", code);
            return ResponseEntity.ok(Map.of("success", true, "message", "Точка локализации удалена"));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Ошибка при удалении: " + e.getMessage()));
        }
    }
}