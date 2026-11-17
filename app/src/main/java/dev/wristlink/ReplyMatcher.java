package dev.wristlink;

import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Conservative, session-only matching. A match selects a draft recipient, never authorizes a send.
 */
public final class ReplyMatcher {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  public static final class Notice {
    public final String title, text, date;

    public Notice(String title, String text, String date) {
      this.title = title;
      this.text = text;
      this.date = date;
    }
  }

  public static final class Candidate {
    public final String number, label, subject, date;
    public final boolean group;

    public Candidate(String number, String label, String subject, String date, boolean group) {
      this.number = number;
      this.label = label;
      this.subject = subject;
      this.date = date;
      this.group = group;
    }
  }

  private static String normalized(String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value.replace("\r\n", "\n").strip(), Normalizer.Form.NFC);
  }

  private static String phone(String value) {
    try {
      return OutgoingMessage.number(value);
    } catch (IllegalArgumentException invalid) {
      return "";
    }
  }

  private static boolean usableText(String value) {
    return !value.isBlank()
        && !Set.of(
                "message",
                "new message",
                "text message",
                "imessage",
                "photo",
                "attachment",
                "notification",
                "text",
                "you have a new message")
            .contains(value.toLowerCase(Locale.ROOT));
  }

  private static Instant timestamp(String value) {
    if (value == null || value.length() < 15) throw new DateTimeException("Missing date");
    LocalDateTime local = LocalDateTime.parse(value.substring(0, 15), DATE);
    if (value.length() == 15) return local.atZone(ZoneId.systemDefault()).toInstant();
    String suffix = value.substring(15);
    if (!suffix.matches("Z|[+-][0-9]{2}:?[0-9]{2}")) throw new DateTimeException("Invalid offset");
    return local.atOffset(ZoneOffset.of(suffix)).toInstant();
  }

  public static long secondsApart(String one, String two) {
    try {
      return Math.abs(Duration.between(timestamp(one), timestamp(two)).getSeconds());
    } catch (RuntimeException malformed) {
      return Long.MAX_VALUE;
    }
  }

  public static boolean groupRecipients(String recipients) {
    return recipients != null && (recipients.contains(",") || recipients.contains(";"));
  }

  public static int match(Notice notice, List<Candidate> candidates) {
    String body = normalized(notice.text);
    if (!usableText(body)) return -1;
    int match = -1;
    for (int i = 0; i < candidates.size(); i++) {
      Candidate candidate = candidates.get(i);
      if (candidate.group
          || phone(candidate.number).isEmpty()
          || !body.equals(normalized(candidate.subject))
          || secondsApart(notice.date, candidate.date) > 5) continue;
      // A conflicting name/number supplied directly by MAP rules this candidate out.
      String suppliedName = contactName(candidate.label), noticeName = contactName(notice.title);
      if (!suppliedName.isEmpty()
          && !noticeName.isEmpty()
          && !normalized(suppliedName).equalsIgnoreCase(normalized(noticeName))) continue;
      String titlePhone = phone(notice.title);
      if (!titlePhone.isEmpty() && !titlePhone.equals(phone(candidate.number))) continue;
      if (match != -1) return -1; // Even duplicate entries for one number require manual selection.
      match = i;
    }
    return match;
  }

  public static String contactName(String label) {
    String name = normalized(label);
    if (name.isEmpty()
        || !phone(name).isEmpty()
        || name.contains("@")
        || name.matches("[+0-9 ()\\-.,;]+")
        || Set.of("messages", "message", "imessage", "unknown", "unknown sender", "group message")
            .contains(name.toLowerCase(Locale.ROOT))) return "";
    return name;
  }

  public static String nameFor(int index, List<Candidate> candidates, List<Notice> notices) {
    Candidate candidate = candidates.get(index);
    String direct = contactName(candidate.label);
    if (!direct.isEmpty()) return direct;
    String found = "";
    for (Notice notice : notices) {
      int matched = match(notice, candidates);
      if (matched < 0
          || !phone(candidates.get(matched).number).equals(phone(candidate.number))
          || phone(candidate.number).isEmpty()) continue;
      String name = contactName(notice.title);
      if (name.isEmpty()) continue;
      if (!found.isEmpty() && !found.equals(name)) return "";
      found = name;
    }
    return found;
  }

  /** Only counts, validity flags, and elapsed-time differences; never content or identifiers. */
  public static String diagnostics(Notice notice, List<Candidate> candidates) {
    int texts = 0, near = 0, exactNear = 0, groups = 0, phones = 0, invalidDates = 0;
    long closest = Long.MAX_VALUE;
    for (Candidate c : candidates) {
      boolean same = normalized(notice.text).equals(normalized(c.subject));
      long delta = secondsApart(notice.date, c.date);
      if (c.group) groups++;
      if (!phone(c.number).isEmpty()) phones++;
      if (secondsApart(c.date, c.date) == Long.MAX_VALUE) invalidDates++;
      if (delta <= 5) near++;
      if (same) {
        texts++;
        closest = Math.min(closest, delta);
        if (delta <= 5) exactNear++;
      }
    }
    return "exactText="
        + texts
        + "; nearTime="
        + near
        + "; exactTextAndTime="
        + exactNear
        + "; groupHints="
        + groups
        + "; validPhones="
        + phones
        + "; noticeDateValid="
        + (secondsApart(notice.date, notice.date) != Long.MAX_VALUE)
        + "; invalidMapDates="
        + invalidDates
        + "; closestExactSeconds="
        + (closest == Long.MAX_VALUE ? -1 : closest);
  }
}
