package by.findsport.web.dto;

/** Согласие на персонализацию (п. 4.8.1) и e-mail-уведомления (п. 4.2.5, 4.2.11). */
public record SettingsRequest(boolean personalizationConsent, boolean emailNotifications) {
}
