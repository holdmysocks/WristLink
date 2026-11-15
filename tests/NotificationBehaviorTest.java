import dev.wristlink.ActionPolicy;
import dev.wristlink.DismissalKey;

public class NotificationBehaviorTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(ActionPolicy.canAutoDismiss(4,true,"Clear"));
        check(ActionPolicy.canAutoDismiss(6,true," dismiss "));
        check(!ActionPolicy.canAutoDismiss(1,true,"Clear"));
        check(!ActionPolicy.canAutoDismiss(4,false,"Clear"));
        for (String action : new String[]{"Decline","Delete","Unsubscribe","Pay","Reply","", "Clear all"}) check(!ActionPolicy.canAutoDismiss(4,true,action));
        String first=DismissalKey.of("app","sender","same text","20261114T150000");
        check(first.length()==64);
        check(first.equals(DismissalKey.of("app","sender","same text","20261114T150000")));
        check(!first.equals(DismissalKey.of("app","sender","same text","20261114T150001")));
        check(!first.equals(DismissalKey.of("other.app","sender","same text","20261114T150000")));
        check(DismissalKey.of("app","sender","text","").isEmpty());
        check(!DismissalKey.of("ab","c","d","date").equals(DismissalKey.of("a","bc","d","date")));
        System.out.println("PASS: dismissal survives session identity changes, distinguishes new messages, missing-date fallback, action semantics");
    }
}
