package dev.aifabric.course.support.web;

import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import dev.aifabric.course.support.message.SupportMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/messages")
public class SupportMessageController {

    private final SupportMessageService messageService;
    private final CoursePrincipalProvider principalProvider;

    public SupportMessageController(SupportMessageService messageService,
                                    CoursePrincipalProvider principalProvider) {
        this.messageService = messageService;
        this.principalProvider = principalProvider;
    }

    @PostMapping
    public SupportMessageService.MessageView submit(@Valid @RequestBody MessageRequest request) {
        return messageService.submit(request.content(), principalProvider.currentPrincipal());
    }

    @GetMapping
    public List<SupportMessageService.MessageView> list() {
        return messageService.list(principalProvider.currentPrincipal());
    }

    public record MessageRequest(@NotBlank @Size(max = 2_000) String content) {
    }
}
