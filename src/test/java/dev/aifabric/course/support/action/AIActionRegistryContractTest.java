package dev.aifabric.course.support.action;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.AIActionRegistryContributor;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.annotation.AIAction;
import ai.fabric.intent.action.annotation.ActionExecute;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.ConversionService;

class AIActionRegistryContractTest {

    @Test
    void duplicateActionNamesFailRegistryConstruction() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("duplicateActionOne", DuplicateActionOne.class);
            context.registerBean("duplicateActionTwo", DuplicateActionTwo.class);
            context.refresh();

            AIActionRegistry registry = registry(context);
            assertThatThrownBy(registry::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registered twice");
        }
    }

    @Test
    void actionWithoutExactlyOneExecuteMethodFailsRegistryConstruction() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("missingExecuteAction", MissingExecuteAction.class);
            context.refresh();

            AIActionRegistry registry = registry(context);
            assertThatThrownBy(registry::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one @ActionExecute");
        }
    }

    private AIActionRegistry registry(GenericApplicationContext context) {
        return new AIActionRegistry(
            context,
            context.getBeanProvider(ConversionService.class),
            context.getBeanProvider(ObjectMapper.class),
            context.getBeanProvider(AIActionRegistryContributor.class)
        );
    }

    @AIAction(
        name = "duplicate_course_action",
        description = "First duplicate",
        accessMode = ActionAccessMode.READ,
        requiresConfirmation = false
    )
    static class DuplicateActionOne {
        @ActionExecute
        ActionResult execute() {
            return ActionResult.builder().success(true).build();
        }
    }

    @AIAction(
        name = "duplicate_course_action",
        description = "Second duplicate",
        accessMode = ActionAccessMode.READ,
        requiresConfirmation = false
    )
    static class DuplicateActionTwo {
        @ActionExecute
        ActionResult execute() {
            return ActionResult.builder().success(true).build();
        }
    }

    @AIAction(
        name = "missing_execute_course_action",
        description = "Invalid action used to prove startup validation",
        accessMode = ActionAccessMode.READ,
        requiresConfirmation = false
    )
    static class MissingExecuteAction {
    }
}
