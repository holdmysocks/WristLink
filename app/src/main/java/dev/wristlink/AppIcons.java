package dev.wristlink;

import android.graphics.*;
import java.util.Locale;

/** Bundled app-recognition artwork. ANCS has no icon attribute or icon download. */
public final class AppIcons {
    public static Bitmap bitmap(String app, String name) {
        String id = app.toLowerCase(Locale.ROOT), label = name.toLowerCase(Locale.ROOT);
        Bitmap result = Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean messages = id.equals("com.apple.mobilesms"), phone = id.equals("com.apple.mobilephone");
        boolean snap = label.equals("snapchat"), paypal = label.equals("paypal"), mail = id.equals("com.apple.mobilemail");
        p.setColor(Color.parseColor(snap ? "#FFFC00" : paypal ? "#003087" : messages || phone ? "#30D158" : mail ? "#1686ED" : "#334C63"));
        canvas.drawRoundRect(0,0,96,96,24,24,p); p.setColor(Color.WHITE);
        if (messages) {
            canvas.drawOval(16,22,80,70,p); Path tail = new Path(); tail.moveTo(28,62); tail.lineTo(23,78); tail.lineTo(48,66); tail.close(); canvas.drawPath(tail,p);
        } else if (mail) {
            canvas.drawRoundRect(15,25,81,71,6,6,p); p.setColor(Color.parseColor("#1686ED")); p.setStrokeWidth(4); p.setStyle(Paint.Style.STROKE);
            Path flap = new Path(); flap.moveTo(16,27); flap.lineTo(48,51); flap.lineTo(80,27); canvas.drawPath(flap,p);
        } else if (phone) {
            p.setStrokeWidth(13); p.setStrokeCap(Paint.Cap.ROUND); p.setStyle(Paint.Style.STROKE);
            Path handset = new Path(); handset.moveTo(28,24); handset.cubicTo(13,47,50,85,73,66); canvas.drawPath(handset,p);
            p.setStyle(Paint.Style.FILL); canvas.save(); canvas.rotate(-35,28,28); canvas.drawRoundRect(20,17,36,39,5,5,p); canvas.restore();
            canvas.save(); canvas.rotate(-35,69,66); canvas.drawRoundRect(61,55,77,77,5,5,p); canvas.restore();
        } else if (snap) {
            Path ghost = new Path(); ghost.moveTo(29,46); ghost.cubicTo(24,11,73,11,68,46); ghost.lineTo(78,56); ghost.lineTo(68,61); ghost.lineTo(72,72); ghost.lineTo(59,69); ghost.lineTo(48,76); ghost.lineTo(37,69); ghost.lineTo(24,72); ghost.lineTo(28,61); ghost.lineTo(18,56); ghost.close();
            canvas.drawPath(ghost,p); p.setColor(Color.BLACK); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); canvas.drawPath(ghost,p);
        } else {
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(paypal ? 72 : 48);
            canvas.drawText(paypal ? "P" : (name.isBlank() || name.equals("iPhone app") ? "•" : name.substring(0,1).toUpperCase(Locale.ROOT)),48,66,p);
        }
        return result;
    }
}
