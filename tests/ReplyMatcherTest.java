import dev.wristlink.*;
import java.util.*;

public final class ReplyMatcherTest {
  static void check(boolean ok) {
    if (!ok) throw new AssertionError();
  }

  static ReplyMatcher.Candidate candidate(String number, String name, String body, String date) {
    return new ReplyMatcher.Candidate(number, name, body, date, false);
  }

  public static void main(String[] args) {
    var notice = new ReplyMatcher.Notice("Alice", "Meet at five?", "20261116T120000");
    var alice = candidate("+12025550143", "+12025550143", "Meet at five?", "20261116T115958");
    var bob = candidate("+12025550144", "+12025550144", "Different text", "20261116T120000");
    var list = List.of(alice, bob);
    check(ReplyMatcher.match(notice, list) == 0);
    check(ReplyMatcher.nameFor(0, list, List.of(notice)).equals("Alice"));
    check(ReplyMatcher.nameFor(1, list, List.of(notice)).isEmpty());
    var duplicate = candidate("+12025550144", "", "Meet at five?", "20261116T120001");
    check(ReplyMatcher.match(notice, List.of(alice, duplicate)) == -1);
    check(ReplyMatcher.nameFor(0, List.of(alice, duplicate), List.of(notice)).isEmpty());
    check(ReplyMatcher.match(notice, List.of(alice, alice)) == -1);
    check(
        ReplyMatcher.match(
                notice,
                List.of(candidate("+12025550143", "Bob", "Meet at five?", "20261116T120000")))
            == -1);
    check(
        ReplyMatcher.match(
                notice, List.of(candidate("+12025550143", "", "Meet at five?", "20261116T120006")))
            == -1);
    check(
        ReplyMatcher.match(notice, List.of(candidate("+12025550143", "", "Meet at five?", "")))
            == -1);
    check(
        ReplyMatcher.match(
                notice,
                List.of(candidate("x@example.com", "Alice", "Meet at five?", "20261116T120000")))
            == -1);
    check(
        ReplyMatcher.match(
                notice,
                List.of(
                    new ReplyMatcher.Candidate(
                        "+12025550143", "Alice", "Meet at five?", "20261116T120000", true)))
            == -1);
    check(
        ReplyMatcher.match(
                new ReplyMatcher.Notice("+12025550144", "Meet at five?", "20261116T120000"),
                List.of(alice))
            == -1);
    check(
        ReplyMatcher.match(
                new ReplyMatcher.Notice(
                    "Alice", "Meet at five? and bring snacks", "20261116T120000"),
                list)
            == -1);
    check(
        ReplyMatcher.match(
                new ReplyMatcher.Notice("Alice", "New message", "20261116T120000"),
                List.of(candidate("+12025550143", "", "New message", "20261116T120000")))
            == -1);
    check(
        ReplyMatcher.match(
                new ReplyMatcher.Notice("Alice", "Cafe\u0301 🌎\r\nYes", "20261116T120000"),
                List.of(candidate("+12025550143", "", "Café 🌎\nYes", "20261116T120000")))
            == 0);
    check(ReplyMatcher.secondsApart("20260230T120000", "20261116T120000") == Long.MAX_VALUE);
    check(ReplyMatcher.secondsApart("20261115T235958", "20261116T000002") == 4);
    check(ReplyMatcher.contactName("+1 (202) 555-0143").isEmpty());
    check(ReplyMatcher.contactName("Messages").isEmpty());
    check(ReplyMatcher.contactName("Alice").equals("Alice"));
    var conflictingNotice =
        new ReplyMatcher.Notice("Different name", "Meet at five?", "20261116T120000");
    check(ReplyMatcher.nameFor(0, list, List.of(notice, conflictingNotice)).isEmpty());
    check(
        ReplyMatcher.nameFor(0, List.of(candidate("+12025550143", "Alice", "", "")), List.of())
            .equals("Alice"));
    check(ReplyMatcher.secondsApart("20261116T170000Z", "20261116T120000-0500") == 0);
    check(ReplyMatcher.secondsApart("20261116T170000+00:00", "20261116T120001-05:00") == 1);
    check(ReplyMatcher.secondsApart("20261116T170000+2500", "20261116T120000") == Long.MAX_VALUE);
    var local =
        java.time.LocalDateTime.of(2026, 11, 16, 12, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant();
    String utc =
        java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
            .format(local);
    check(ReplyMatcher.secondsApart("20261116T120000", utc) == 0);
    check(!ReplyMatcher.groupRecipients("+12025550143"));
    check(ReplyMatcher.groupRecipients("+12025550143;+12025550144"));
    var samePersonOlder = candidate("+12025550143", "", "Older text", "20261116T110000");
    check(
        ReplyMatcher.nameFor(1, List.of(alice, samePersonOlder), List.of(notice)).equals("Alice"));
    String diagnostic = ReplyMatcher.diagnostics(notice, list);
    check(!diagnostic.contains("Alice"));
    check(!diagnostic.contains("20261116"));
    check(!diagnostic.contains("12025550143"));
    check(!diagnostic.contains("Meet"));
    check(diagnostic.contains("exactTextAndTime=1"));
    System.out.println(
        "PASS: unique exact text/time matching, names, duplicate senders, stale/invalid dates,"
            + " groups, hidden/truncated previews, conflicting identities, Unicode");
  }
}
