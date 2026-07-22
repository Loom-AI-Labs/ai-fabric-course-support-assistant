package dev.aifabric.course.support.web;

import dev.aifabric.course.support.migration.KnowledgeMigrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/migrations/knowledge-articles")
public class KnowledgeMigrationController {

    private final KnowledgeMigrationService migrationService;

    public KnowledgeMigrationController(KnowledgeMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping
    public KnowledgeMigrationService.MigrationView start(
        @Valid @RequestBody(required = false) KnowledgeMigrationService.StartMigrationRequest request
    ) {
        return migrationService.start(request);
    }

    @GetMapping
    public List<KnowledgeMigrationService.MigrationView> list() {
        return migrationService.list();
    }

    @GetMapping("/{jobId}")
    public KnowledgeMigrationService.MigrationView get(@PathVariable String jobId) {
        return migrationService.get(jobId);
    }

    @PostMapping("/{jobId}/pause")
    public KnowledgeMigrationService.MigrationView pause(@PathVariable String jobId) {
        return migrationService.pause(jobId);
    }

    @PostMapping("/{jobId}/resume")
    public KnowledgeMigrationService.MigrationView resume(@PathVariable String jobId) {
        return migrationService.resume(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public KnowledgeMigrationService.MigrationView cancel(@PathVariable String jobId) {
        return migrationService.cancel(jobId);
    }
}
