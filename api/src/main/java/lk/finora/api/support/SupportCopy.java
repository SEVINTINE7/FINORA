package lk.finora.api.support;

import lk.finora.api.common.error.ApiException;

/** Approved sentence blocks for help requests and proposed bilingual saved replies. */
enum SupportCopy {
    SIGN_IN("Sign-in", "I need help signing in.", "Check your connection and try signing in again.",
            "ඔබේ අන්තර්ජාල සම්බන්ධතාව පරීක්ෂා කර නැවත පිවිසීමට උත්සාහ කරන්න."),
    NAVIGATION("Navigation", "I need help finding a feature.",
            "Open Records to review transactions, or Plan to manage budgets and savings goals.",
            "ගනුදෙනු පරීක්ෂා කිරීමට වාර්තා විවෘත කරන්න. අයවැය සහ ඉතිරිකිරීමේ ඉලක්ක කළමනාකරණය කිරීමට සැලසුම විවෘත කරන්න."),
    SMS_PERMISSION("SMS permission", "I need help with SMS permission.",
            "SMS access is optional. You can enter a transaction manually or import a CSV file without it.",
            "SMS ප්‍රවේශය අත්‍යවශ්‍ය නොවේ. SMS ප්‍රවේශයට අවසර නොදී ගනුදෙනුවක් අතින් ඇතුළත් කිරීමට හෝ CSV ගොනුවක් ආයාත කිරීමට හැකිය."),
    LANGUAGE("Language", "I need help changing the app language.",
            "Open Language and choose English or Sinhala. Connect to the internet to save the choice to your account.",
            "භාෂාව විවෘත කර ඉංග්‍රීසි හෝ සිංහල තෝරන්න. තේරීම ඔබේ ගිණුමට සුරැකීමට අන්තර්ජාලයට සම්බන්ධ වන්න.");

    final String subject;
    final String userBody;
    final String replyEn;
    final String replySi;

    SupportCopy(String subject, String userBody, String replyEn, String replySi) {
        this.subject = subject;
        this.userBody = userBody;
        this.replyEn = replyEn;
        this.replySi = replySi;
    }

    static SupportCopy ticket(String subject, String body) {
        for (var option : values()) {
            if (option.subject.equals(subject) && option.userBody.equals(body)) return option;
        }
        throw ApiException.badRequest("Choose a supported help topic");
    }

    static SupportCopy topic(String name) {
        for (var option : values()) if (option.name().equals(name)) return option;
        throw ApiException.badRequest("Unknown saved reply topic");
    }
}
