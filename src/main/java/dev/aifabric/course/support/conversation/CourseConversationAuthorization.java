package dev.aifabric.course.support.conversation;

import dev.aifabric.course.support.account.CustomerAccountRepository;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CourseConversationAuthorization {

    private static final int MAX_CONVERSATION_ID_LENGTH = 128;
    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    private final CustomerAccountRepository accountRepository;

    public CourseConversationAuthorization(CustomerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public boolean canCreate(String ownerId) {
        return knownOwner(ownerId);
    }

    public boolean canAccess(String ownerId, String conversationId) {
        return knownOwner(ownerId) && validConversationId(conversationId);
    }

    public boolean canRecord(String ownerId, String conversationId) {
        return canAccess(ownerId, conversationId);
    }

    public boolean canDelete(String ownerId, String conversationId) {
        return canAccess(ownerId, conversationId);
    }

    private boolean knownOwner(String ownerId) {
        return StringUtils.hasText(ownerId) && accountRepository.existsById(ownerId.trim());
    }

    private boolean validConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        String normalized = conversationId.trim();
        return normalized.length() <= MAX_CONVERSATION_ID_LENGTH
            && CONVERSATION_ID.matcher(normalized).matches();
    }
}
