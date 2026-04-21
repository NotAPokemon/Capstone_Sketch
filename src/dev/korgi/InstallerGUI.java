package dev.korgi;

import dev.korgi.utils.InstallConstants;
import processing.core.PApplet;
import processing.core.PFont;

public class InstallerGUI extends PApplet {

    private static InstallerGUI mInstance = null;

    public static InstallerGUI getInstance() {
        if (mInstance == null) {
            mInstance = new InstallerGUI();
        }
        return mInstance;
    }

    private static final int SCREEN_WELCOME = 0;
    private static final int SCREEN_LICENSE = 1;
    private static final int SCREEN_OPTIONS = 2;
    private static final int SCREEN_INSTALL = 3;
    private static final int SCREEN_DONE = 4;

    private int screen = SCREEN_WELCOME;

    private final int BG = 0xFF0F0F13;
    private final int PANEL = 0xFF1A1A22;
    private final int ACCENT = 0xFF5B8CFF;
    private final int ACCENT2 = 0xFF8F5BFF;
    private final int TEXT_HI = 0xFFE8E8F0;
    private final int TEXT_MID = 0xFF9090A8;
    private final int TEXT_DIM = 0xFF44445A;
    private final int SUCCESS = 0xFF4DFFA0;
    private final int BTN_HOVER = 0xFF2A2A38;

    static String installPath = "./";

    private PFont fontBold, fontRegular, fontMono;

    private float progress = 0;
    private float progressTarget = 0;
    private float barPulse = 0;
    private boolean installing = false;
    private boolean installDone = false;
    private int installStep = 0;
    private int lastStepTime = 0;

    private final String[] INSTALL_STEPS = {
            "Initialising file system...",
            "Extracting core libraries...",
            "Configuring runtime environment...",
            "Registering system components...",
            "Writing application data...",
            "Applying user preferences...",
            "Finalising installation...",
            "Installation complete!"
    };
    private final float[] STEP_TARGETS = {
            0.06f, 0.22f, 0.40f, 0.55f, 0.70f, 0.83f, 0.95f, 1.0f
    };

    private volatile boolean asyncTaskDone = false;
    private volatile boolean asyncTaskStarted = false;

    private boolean optDesktop = true;
    private boolean optStartMenu = true;

    private float licenseScroll = 0;
    private float licenseScrollTarget = 0;
    private boolean licenseAccepted = false;

    private float[] starX = new float[120];
    private float[] starY = new float[120];
    private float[] starA = new float[120];
    private float[] starB = new float[120];

    private float checkAlpha[] = new float[INSTALL_STEPS.length];

    private float btnNextHover = 0;
    private float btnBackHover = 0;
    private float btnCancelHover = 0;

    private float doneRadius = 0;
    private float doneAlpha = 0;

    @Override
    public void settings() {
        size(1280, 720);
        smooth(8);
    }

    @Override
    public void setup() {
        fontBold = createFont("Arial Bold", 1, true);
        fontRegular = createFont("Arial", 1, true);
        fontMono = createFont("Courier New", 1, true);

        for (int i = 0; i < starX.length; i++) {
            starX[i] = random(width);
            starY[i] = random(height);
            starA[i] = random(0.1f, 0.7f);
            starB[i] = random(starA[i]);
        }
        frameRate(60);
    }

    @Override
    public void draw() {
        updateLogic();
        drawBackground();
        drawSidebar();
        drawContent();
        drawBottomBar();
    }

    private void updateLogic() {
        barPulse += 0.06f;

        for (int i = 0; i < starX.length; i++) {
            starB[i] = lerp(starB[i], starA[i], 0.03f);
            if (abs(starB[i] - starA[i]) < 0.01f) {
                starA[i] = random(0.05f, 0.6f);
            }
        }

        float cap = asyncTaskDone ? 1.0f : 0.9f;
        progress = lerp(progress, min(progressTarget, cap), 0.04f);

        if (installing && !installDone) {
            int now = millis();
            if (installStep < INSTALL_STEPS.length && now - lastStepTime > 900) {
                progressTarget = STEP_TARGETS[installStep];
                checkAlpha[installStep] = 0;
                installStep++;
                lastStepTime = now;
                if (installStep == INSTALL_STEPS.length) {
                    installDone = true;
                }
            }
        }

        for (int i = 0; i < installStep && i < checkAlpha.length; i++) {
            checkAlpha[i] = lerp(checkAlpha[i], 1f, 0.07f);
        }

        if (screen == SCREEN_DONE) {
            doneRadius = lerp(doneRadius, 80, 0.06f);
            doneAlpha = lerp(doneAlpha, 1, 0.04f);
        }

        btnNextHover = lerp(btnNextHover, isOverNextBtn() ? 1 : 0, 0.15f);
        btnBackHover = lerp(btnBackHover, isOverBackBtn() ? 1 : 0, 0.15f);
        btnCancelHover = lerp(btnCancelHover, isOverCancelBtn() ? 1 : 0, 0.15f);

        licenseScroll = lerp(licenseScroll, licenseScrollTarget, 0.12f);
    }

    private void drawBackground() {
        background(BG);

        stroke(TEXT_DIM, 20);
        strokeWeight(1);
        for (int x = 0; x < width; x += 60) {
            line(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 60) {
            line(0, y, width, y);
        }

        noStroke();
        for (int i = 0; i < starX.length; i++) {
            fill(200, 210, 255, starB[i] * 180);
            ellipse(starX[i], starY[i], 2, 2);
        }

        for (int r = 300; r > 0; r -= 10) {
            fill(91, 140, 255, map(r, 300, 0, 0, 8));
            ellipse(0, 0, r * 2, r * 2);
        }
        for (int r = 250; r > 0; r -= 10) {
            fill(143, 91, 255, map(r, 250, 0, 0, 6));
            ellipse(width, height, r * 2, r * 2);
        }
    }

    private void drawSidebar() {
        fill(PANEL, 230);
        noStroke();
        rect(0, 0, 280, height);

        fill(ACCENT, 60);
        rect(0, 0, 3, height);

        drawAppIcon(140, 80, 48);

        fill(TEXT_HI);
        textFont(fontBold, 20);
        textAlign(CENTER, CENTER);
        text(InstallConstants.appName, 140, 148);

        fill(TEXT_MID);
        textFont(fontRegular, 12);
        text("Version " + InstallConstants.version + "  •  © 2026", 140, 170);

        String[] steps = { "Welcome", "License", "Options", "Install", "Finish" };
        for (int i = 0; i < steps.length; i++) {
            drawSidebarStep(i, steps[i]);
        }

        fill(TEXT_DIM);
        textFont(fontRegular, 11);
        textAlign(CENTER, BOTTOM);
        text("NotAPokemon's capstone Software.", 140, height - 18);
    }

    private void drawAppIcon(float cx, float cy, float r) {
        for (int g = 20; g > 0; g -= 2) {
            fill(ACCENT, map(g, 20, 0, 0, 30));
            ellipse(cx, cy, r * 2 + g * 3, r * 2 + g * 3);
        }

        fill(ACCENT);
        ellipse(cx, cy, r * 2, r * 2);

        fill(255, 255, 255, 60);
        ellipse(cx - r * 0.15f, cy - r * 0.2f, r * 0.8f, r * 0.6f);

        fill(255);
        textFont(fontBold, r * 0.85f);
        textAlign(CENTER, CENTER);
        text("K", cx, cy + 1);
    }

    private void drawSidebarStep(int idx, String label) {
        float y = 240 + idx * 54;
        boolean active = screen == idx;
        boolean done = screen > idx;

        if (idx < 4) {
            stroke(done ? ACCENT : TEXT_DIM, done ? 120 : 50);
            strokeWeight(2);
            line(140, y + 18, 140, y + 54);
        }

        noStroke();
        if (done) {
            fill(ACCENT);
        } else if (active) {
            float pulse = 0.5f + 0.5f * sin(barPulse * 0.7f);
            fill(lerpColor(ACCENT, ACCENT2, pulse));
        } else {
            fill(TEXT_DIM, 80);
        }
        ellipse(140, y, 20, 20);

        if (done) {
            fill(255);
            textFont(fontBold, 11);
            textAlign(CENTER, CENTER);
            text("✓", 140, y + 1);
        } else {
            fill(active ? 255 : TEXT_DIM);
            textFont(fontBold, 10);
            textAlign(CENTER, CENTER);
            text(str(idx + 1), 140, y + 1);
        }

        fill(active ? TEXT_HI : (done ? ACCENT : TEXT_DIM));
        textFont(active ? fontBold : fontRegular, 13);
        textAlign(LEFT, CENTER);
        text(label, 162, y);
    }

    private void drawContent() {
        int cx = 280;
        int cw = width - cx;

        switch (screen) {
            case SCREEN_WELCOME:
                drawWelcome(cx, cw);
                break;
            case SCREEN_LICENSE:
                drawLicense(cx, cw);
                break;
            case SCREEN_OPTIONS:
                drawOptions(cx, cw);
                break;
            case SCREEN_INSTALL:
                drawInstall(cx, cw);
                break;
            case SCREEN_DONE:
                drawDone(cx, cw);
                break;
        }
    }

    private void drawWelcome(int cx, int cw) {
        textAlign(LEFT, TOP);

        fill(TEXT_MID);
        textFont(fontRegular, 13);
        text("SETUP WIZARD", cx + 60, 68);

        fill(TEXT_HI);
        textFont(fontBold, 42);
        text("Welcome!", cx + 60, 95);

        stroke(ACCENT, 180);
        strokeWeight(3);
        line(cx + 60, 210, cx + 60 + 100, 210);
        noStroke();

        fill(TEXT_MID);
        textFont(fontRegular, 15);
        text("This wizard will guide you through the installation\n" +
                "of " + InstallConstants.appName + " " + InstallConstants.version + " on your computer.\n\n" +
                "Click Next to continue, or Cancel to exit setup.", cx + 60, 228);

        String[] features = {
                "⚡  Blazing-fast rendering engine",
                "☁️  Multiplayer support",
                "🧩  Various Config Options"
        };
        for (int i = 0; i < features.length; i++) {
            float fy = 430 + i * 38;
            fill(PANEL, 180);
            noStroke();
            rect(cx + 60, fy - 12, 500, 30, 6);
            fill(TEXT_HI);
            textFont(fontRegular, 13);
            textAlign(LEFT, CENTER);
            text(features[i], cx + 80, fy + 3);
        }
    }

    private void drawLicense(int cx, int cw) {
        textAlign(LEFT, TOP);

        fill(TEXT_MID);
        textFont(fontRegular, 13);
        text("STEP 1 OF 3", cx + 60, 68);

        fill(TEXT_HI);
        textFont(fontBold, 32);
        text("License Agreement", cx + 60, 95);

        int bx = cx + 60, by = 148, bw = cw - 120, bh = 360;
        fill(0xFF080810);
        noStroke();
        rect(bx, by, bw, bh, 8);

        String licText = getLicenseText();
        float lineH = 17f;
        float totalH = 1000f;
        float maxScroll = max(0, totalH - bh + 30);
        licenseScrollTarget = constrain(licenseScrollTarget, 0, maxScroll);

        fill(TEXT_MID);
        textFont(fontMono, 11);
        textLeading(lineH);
        clip(bx, by, bw, bh);
        text(licText, bx + 16, by + 14 - licenseScroll);
        noClip();

        float sbH = map(bh, 0, totalH, 0, bh);
        float sbY = map(licenseScroll, 0, maxScroll, by, by + bh - sbH);
        fill(TEXT_DIM, 120);
        rect(bx + bw - 8, sbY, 5, sbH, 3);

        boolean overCheck = mouseX > bx && mouseX < bx + 200 &&
                mouseY > by + bh + 16 && mouseY < by + bh + 36;
        fill(licenseAccepted ? ACCENT : (overCheck ? BTN_HOVER : PANEL));
        stroke(licenseAccepted ? ACCENT : TEXT_DIM, 180);
        strokeWeight(1.5f);
        rect(bx, by + bh + 16, 18, 18, 4);
        noStroke();
        if (licenseAccepted) {
            fill(255);
            textFont(fontBold, 12);
            textAlign(CENTER, CENTER);
            text("✓", bx + 9, by + bh + 25);
        }
        fill(TEXT_HI);
        textFont(fontRegular, 13);
        textAlign(LEFT, CENTER);
        text("I accept the terms of the License Agreement", bx + 28, by + bh + 25);

        if (!licenseAccepted) {
            fill(TEXT_MID, 120);
            textFont(fontRegular, 12);
            textAlign(LEFT, TOP);
            text("You must accept the license to continue.", bx, by + bh + 44);
        }
    }

    private void drawOptions(int cx, int cw) {
        textAlign(LEFT, TOP);

        fill(TEXT_MID);
        textFont(fontRegular, 13);
        text("STEP 2 OF 3", cx + 60, 68);

        fill(TEXT_HI);
        textFont(fontBold, 32);
        text("Installation Options", cx + 60, 95);

        fill(TEXT_MID);
        textFont(fontRegular, 13);
        text("Destination folder", cx + 60, 158);

        fill(0xFF080810);
        noStroke();
        rect(cx + 60, 178, cw - 120, 38, 6);
        fill(TEXT_MID);
        textFont(fontMono, 12);
        textAlign(LEFT, CENTER);
        text(installPath + "Korgi/" + InstallConstants.appName + "/", cx + 76, 197);

        fill(BTN_HOVER);
        rect(cx + cw - 160, 178, 80, 38, 6);
        fill(TEXT_HI);
        textFont(fontRegular, 12);
        textAlign(CENTER, CENTER);
        text("Browse...", cx + cw - 120, 197);

        fill(TEXT_DIM);
        textFont(fontRegular, 11);
        textAlign(LEFT, TOP);
        text("Space required: 148 MB     Space available: 234 GB", cx + 60, 225);

        fill(TEXT_HI);
        textFont(fontBold, 16);
        text("Create shortcuts", cx + 60, 278);

        stroke(TEXT_DIM, 40);
        strokeWeight(1);
        line(cx + 60, 300, cx + cw - 60, 300);
        noStroke();

        String[] optLabels = {
                "Desktop shortcut",
                "Start Menu entry"
        };
        boolean[] optVals = { optDesktop, optStartMenu };

        for (int i = 0; i < optLabels.length; i++) {
            float oy = 318 + i * 52;
            boolean ov = optVals[i];
            boolean over = mouseX > cx + 60 && mouseX < cx + 60 + 360 &&
                    mouseY > oy - 8 && mouseY < oy + 28;

            fill(over ? BTN_HOVER : PANEL, 180);
            noStroke();
            rect(cx + 60, oy - 8, 500, 38, 8);

            float tx = cx + 60 + 455, ty = oy + 11;
            fill(ov ? ACCENT : TEXT_DIM, ov ? 255 : 100);
            rect(tx, ty, 38, 18, 9);
            fill(255);
            ellipse(ov ? tx + 28 : tx + 10, ty + 9, 14, 14);

            fill(TEXT_HI);
            textFont(fontRegular, 14);
            textAlign(LEFT, CENTER);
            text(optLabels[i], cx + 84, oy + 11);
        }
    }

    private void drawInstall(int cx, int cw) {
        textAlign(LEFT, TOP);

        fill(TEXT_MID);
        textFont(fontRegular, 13);
        text("STEP 3 OF 3", cx + 60, 68);

        fill(TEXT_HI);
        textFont(fontBold, 32);
        text("Installing " + InstallConstants.appName, cx + 60, 95);

        int bx = cx + 60, by = 174, bw = cw - 120, bh = 14;
        fill(0xFF080810);
        noStroke();
        rect(bx, by, bw, bh, bh / 2);

        float pw = progress * bw;
        if (pw > 0) {
            float t = 0.5f + 0.5f * sin(barPulse);
            int col1 = lerpColor(ACCENT, ACCENT2, t);
            int col2 = lerpColor(ACCENT2, ACCENT, t);
            for (int x = 0; x < (int) pw; x += 4) {
                float mix = (float) x / pw;
                fill(lerpColor(col1, col2, mix));
                rect(bx + x, by, min(4, pw - x), bh);
            }
            float shimX = bx + (barPulse * 40 % pw);
            fill(255, 255, 255, 40);
            rect(shimX, by, 30, bh);
        }

        fill(TEXT_HI);
        textFont(fontBold, 26);
        textAlign(RIGHT, TOP);
        text(nf(progress * 100, 0, 1) + "%", cx + cw - 60, 198);
        textAlign(LEFT, TOP);

        for (int i = 0; i < installStep && i < INSTALL_STEPS.length; i++) {
            float ly = 230 + i * 36;
            boolean isLast = (i == installStep - 1) && !installDone;

            float alpha = (i < installStep - 2) ? 0.4f : 1f;

            fill(lerpColor(0xFF080810, SUCCESS, checkAlpha[i]), (int) (alpha * 255));
            ellipse(bx + 10, ly + 10, 16, 16);
            if (checkAlpha[i] > 0.1f) {
                fill(0xFF080810, (int) (checkAlpha[i] * alpha * 255));
                textFont(fontBold, 9);
                textAlign(CENTER, CENTER);
                text("✓", bx + 10, ly + 10);
            }

            fill(isLast ? TEXT_HI : TEXT_MID, (int) (alpha * 255));
            textFont(isLast ? fontBold : fontRegular, 13);
            textAlign(LEFT, TOP);
            if (isLast) {
                String label = INSTALL_STEPS[i];
                if ((frameCount / 30) % 2 == 0)
                    label += " _";
                text(label, bx + 28, ly + 3);
            } else {
                text(INSTALL_STEPS[i], bx + 28, ly + 3);
            }
        }
    }

    private void drawDone(int cx, int cw) {
        float midX = cx + cw / 2f;
        float midY = height / 2f - 40;

        for (int r = (int) doneRadius; r > 0; r -= 6) {
            fill(SUCCESS, map(r, (int) doneRadius, 0, 0, 20) * doneAlpha);
            ellipse(midX, midY, r * 2, r * 2);
        }

        fill(SUCCESS);
        ellipse(midX, midY, 100, 100);
        fill(BG);
        textFont(fontBold, 46);
        textAlign(CENTER, CENTER);
        text("✓", midX, midY + 2);

        fill(TEXT_HI, (int) (doneAlpha * 255));
        textFont(fontBold, 36);
        text("Installation Complete!", midX, midY + 86);

        fill(TEXT_MID, (int) (doneAlpha * 200));
        textFont(fontRegular, 15);
        text(InstallConstants.appName + " " + InstallConstants.version
                + "has been installed successfully.\nClick Finish to exit the Setup Wizard.", midX,
                midY + 134);
    }

    private void drawBottomBar() {
        stroke(TEXT_DIM, 50);
        strokeWeight(1);
        line(280, height - 68, width, height - 68);
        noStroke();

        fill(PANEL, 200);
        rect(280, height - 68, width - 280, 68);

        if (screen < SCREEN_DONE) {
            float ca = btnCancelHover;
            fill(lerpColor(0xFF000000, BTN_HOVER, ca));
            rect(width - 310, height - 50, 90, 34, 6);
            fill(lerpColor(TEXT_MID, TEXT_HI, ca));
            textFont(fontRegular, 13);
            textAlign(CENTER, CENTER);
            text("Cancel", width - 265, height - 33);
        }

        if (screen > SCREEN_WELCOME && screen < SCREEN_INSTALL) {
            float ba = btnBackHover;
            fill(lerpColor(0xFF000000, BTN_HOVER, ba));
            rect(width - 210, height - 50, 90, 34, 6);
            fill(lerpColor(TEXT_MID, TEXT_HI, ba));
            textFont(fontRegular, 13);
            textAlign(CENTER, CENTER);
            text("< Back", width - 165, height - 33);
        }

        boolean canNext = canAdvance();
        String nextLabel = screen == SCREEN_OPTIONS ? "Install"
                : screen == SCREEN_DONE ? "Finish"
                        : screen == SCREEN_INSTALL ? (installDone ? "Next >" : "...")
                                : "Next >";
        float na = btnNextHover;
        int btnCol = canNext
                ? lerpColor(ACCENT, lerpColor(ACCENT, 0xFFFFFFFF, 0.15f), na)
                : lerpColor(TEXT_DIM, TEXT_DIM, 1f);
        fill(btnCol);
        rect(width - 110, height - 50, 90, 34, 6);
        fill(canNext ? color(255) : TEXT_DIM);
        textFont(fontBold, 13);
        textAlign(CENTER, CENTER);
        text(nextLabel, width - 65, height - 33);
    }

    @Override
    public void mousePressed() {
        if (isOverNextBtn() && canAdvance()) {
            nextScreen();
        }
        if (isOverBackBtn() && screen > SCREEN_WELCOME && screen < SCREEN_INSTALL) {
            screen--;
        }
        if (isOverCancelBtn() && screen < SCREEN_DONE) {
            exit();
        }

        if (screen == SCREEN_LICENSE) {
            int bx = 280 + 60, by = 148, bh = 360;
            if (mouseX > bx && mouseX < bx + 200 &&
                    mouseY > by + bh + 16 && mouseY < by + bh + 36) {
                licenseAccepted = !licenseAccepted;
            }
        }

        if (screen == SCREEN_OPTIONS) {
            int cx = 280;
            int cw = width - 280;
            if (mouseX > cx + cw - 160 && mouseX < cx + cw - 80 &&
                    mouseY > 178 && mouseY < 216) {
                selectFolder("Select installation folder:", "folderSelected");
            }
            float[] oys = { 318, 370, 422 };
            for (int i = 0; i < oys.length; i++) {
                float oy = oys[i];
                if (mouseX > cx + 60 && mouseX < cx + 60 + 560 &&
                        mouseY > oy - 8 && mouseY < oy + 30) {
                    if (i == 0)
                        optDesktop = !optDesktop;
                    if (i == 1)
                        optStartMenu = !optStartMenu;
                }
            }
        }

    }

    public void folderSelected(java.io.File selection) {
        if (selection != null) {
            installPath = selection.getAbsolutePath() + java.io.File.separator;
        }
    }

    @Override
    public void mouseWheel(processing.event.MouseEvent e) {
        if (screen == SCREEN_LICENSE) {
            licenseScrollTarget = constrain(licenseScrollTarget + e.getCount() * 20, 0, 800);
        }
    }

    private void nextScreen() {
        if (screen == SCREEN_INSTALL) {
            if (installDone)
                screen = SCREEN_DONE;
            return;
        }
        if (screen == SCREEN_DONE) {
            exit();
            return;
        }
        screen++;
        if (screen == SCREEN_INSTALL) {
            installing = true;
            lastStepTime = millis();
            if (!asyncTaskStarted) {
                asyncTaskStarted = true;
                runAsyncTask();
            }
        }
    }

    private boolean canAdvance() {
        if (screen == SCREEN_LICENSE && !licenseAccepted)
            return false;
        if (screen == SCREEN_INSTALL && !installDone)
            return false;
        return true;
    }

    private boolean isOverNextBtn() {
        return mouseX > width - 110 && mouseX < width - 20 &&
                mouseY > height - 50 && mouseY < height - 16;
    }

    private boolean isOverBackBtn() {
        return mouseX > width - 210 && mouseX < width - 120 &&
                mouseY > height - 50 && mouseY < height - 16;
    }

    private boolean isOverCancelBtn() {
        return mouseX > width - 310 && mouseX < width - 220 &&
                mouseY > height - 50 && mouseY < height - 16;
    }

    private void runAsyncTask() {
        new Thread(() -> {
            try {
                doHeavyWork();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                asyncTaskDone = true;
            }
        }, "korgi-async-task").start();
    }

    private void doHeavyWork() throws Exception {
        Installer.runInstall();
    }

    private String getLicenseText() {
        return "END-USER LICENSE AGREEMENT\n" +
                InstallConstants.appName + " " + InstallConstants.version + "\n\n" +
                "IMPORTANT - READ CAREFULLY\n\n" +
                "This End-User License Agreement (\"EULA\") is a legal agreement between\n" +
                "you (either an individual or a single entity) and NotAPokemon's capstone group.\n" +
                "(\"NotAPokemon's capstone group\") for the " + InstallConstants.appName
                + " software product identified above, which\n" +
                "includes computer software and may include associated media, printed\n" +
                "materials, and online or electronic documentation.\n\n" +
                "By installing, copying, or otherwise using " + InstallConstants.appName + ", you agree to be\n" +
                "bound by the terms of this EULA. If you do not agree to the terms of\n" +
                "this EULA, do not install or use " + InstallConstants.appName + ".\n\n" +
                "1. GRANT OF LICENSE\n" +
                "NotAPokemon's capstone group grants you a non-exclusive, non-transferable, limited license\n" +
                "to install and use " + InstallConstants.appName + " solely for your personal, non-commercial\n" +
                "purposes on a single computer owned or controlled by you.\n\n" +
                "2. RESTRICTIONS\n" +
                "You may not: (a) copy or duplicate " + InstallConstants.appName + "; (b) sell, resell, assign,\n" +
                "transfer, sublicense, or distribute " + InstallConstants.appName + "; (c) reverse-engineer,\n" +
                "decompile, disassemble, or attempt to derive source code; (d) modify\n" +
                "or create derivative works; (e) remove any proprietary notices.\n\n" +
                "3. INTELLECTUAL PROPERTY\n" +
                "" + InstallConstants.appName + " is protected by copyright laws and international copyright\n" +
                "treaties. NotAPokemon's capstone group retains all intellectual property rights in "
                + InstallConstants.appName + ".\n\n" +
                "4. DISCLAIMER OF WARRANTIES\n" +
                "" + InstallConstants.appName.toUpperCase()
                + " IS PROVIDED \"AS IS\" WITHOUT WARRANTY OF ANY KIND, EXPRESS\n" +
                "OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF\n" +
                "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.\n\n" +
                "5. LIMITATION OF LIABILITY\n" +
                "IN NO EVENT SHALL NotAPokemon's capstone group BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL,\n" +
                "EXEMPLARY, OR CONSEQUENTIAL DAMAGES, HOWEVER CAUSED.\n\n" +
                "6. TERMINATION\n" +
                "This EULA is effective until terminated. Your rights under this EULA will\n" +
                "terminate automatically without notice if you fail to comply with any\n" +
                "of its terms and conditions.\n\n" +
                "7. GOVERNING LAW\n" +
                "This EULA shall be governed by the laws of the United States Of America.\n\n" +
                "8. ENTIRE AGREEMENT\n" +
                "This EULA constitutes the entire agreement between you and NotAPokemon's capstone group relating\n" +
                "to " + InstallConstants.appName + " and supersedes all prior or contemporaneous understandings.\n\n" +
                "© 2026. All rights reserved.";
    }

}