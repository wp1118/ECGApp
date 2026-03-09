package com.ecg.drawer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ECGView ecgView;
    private TextView tvStatus;
    private TextView tvStage;
    private Button btnReset;
    private Button btnGuide;
    private Button btnContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvStage = findViewById(R.id.tvStage);
        btnReset = findViewById(R.id.btnReset);
        btnGuide = findViewById(R.id.btnGuide);
        btnContact = findViewById(R.id.btnContact);

        LinearLayout container = findViewById(R.id.ecgContainer);
        ecgView = new ECGView(this);
        container.addView(ecgView);

        btnReset.setOnClickListener(v -> ecgView.reset());
        btnGuide.setOnClickListener(v -> showGuide());
        btnContact.setOnClickListener(v -> showContactDialog());
    }

    private void showGuide() {
        new AlertDialog.Builder(this)
            .setTitle("标准")
            .setMessage(getGuideText())
            .setPositiveButton("确定", null)
            .show();
    }

    private void showContactDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        
        TextView textView = new TextView(this);
        textView.setText(getContactText());
        textView.setTextSize(12);
        textView.setLineSpacing(1.3f, 1.3f);
        textView.setPadding(0, 0, 0, 20);
        layout.addView(textView);
        
        ImageView qrImage = new ImageView(this);
        qrImage.setImageResource(R.drawable.donation_qr);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            500
        );
        qrImage.setLayoutParams(params);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(qrImage);
        
        scrollView.addView(layout);
        
        new AlertDialog.Builder(this)
            .setTitle("联系")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show();
    }

    private String getGuideText() {
        return "【P波】<0.12s(3格)，<0.25mv(2.5格)，向上\n\n" +
               "【PR间期】0.12-0.20s(3-5格)，水平\n\n" +
               "【QRS】Q<1格，R>5格，总<3格\n\n" +
               "【ST段】压低<0.5格，无弓背上抬\n\n" +
               "【T波】向上，>R/10且<5格";
    }

    private String getContactText() {
        return "本软件乃捐赠版（Donationware），版权归Sean & Dean所有，专为医林同道、杏林学子备之。\n\n" +
               "若君用得顺手，愿赏几文润笔，助我等勉力改进，则感激涕零，叩首以谢！\n\n" +
               "赏银随意，不拘多寡，长按下方玄机之码，微信支付宝皆可通也。\n\n" +
               "如有高见或疑问，欢迎传书至：wangdgj@qq.com\n\n" +
               "多谢诸位！";
    }

    public void updateStatus(String status) {
        tvStatus.setText(status);
    }

    public void updateStage(String stage) {
        tvStage.setText("当前：" + stage);
    }

    public void showFinalResult(boolean success, String message) {
        new AlertDialog.Builder(this)
            .setTitle(success ? "完成" : "有异常")
            .setMessage(message)
            .setPositiveButton("重绘", (dialog, which) -> ecgView.reset())
            .setCancelable(false)
            .show();
    }

    class ECGView extends View {
        private Paint gridPaint;
        private Paint ecgPaint;
        private Paint activePaint;
        private Paint textPaint;
        private Paint startPointPaint;
        
        // 坐标纸扩大一倍：50f -> 100f
        private float smallGridSize = 100f;
        private float bigGridSize = smallGridSize * 5;
        
        private Path drawnPath;
        private List<PointF> currentPathPoints;
        private float lastX = 0;
        private float lastY = 0;
        private float baselineY = 0;
        private float startX = 100;
        
        private int currentStage = 0;
        private String[] stageNames = {"P波", "PR间期", "QRS波群", "ST段", "T波"};
        private PointF startPoint;
        private PointF endPoint;
        
        // 记录各波形的振幅
        private float pWaveAmp = 0;
        private float qWaveAmp = 0;
        private float rWaveAmp = 0;
        private float sWaveAmp = 0;
        private float tWaveAmp = 0;
        
        // 标准值
        private final float P_MAX_TIME = 0.12f;
        private final float P_MAX_AMP = 0.25f;
        private final float PR_MIN_TIME = 0.12f;
        private final float PR_MAX_TIME = 0.20f;
        private final float Q_MAX_TIME = 0.04f;
        private final float Q_MAX_RATIO = 0.25f;
        private final float QRS_MAX_TIME = 0.12f;
        private final float ST_ELEVATION_MAX = 0.1f;
        private final float ST_DEPRESSION_MAX = 0.05f;
        private final float T_MAX_AMP = 0.5f;
        private final float T_MIN_RATIO = 0.1f;
        
        private float secondsPerSmallGrid = 0.04f;
        private float mvPerSmallGrid = 0.1f;
        
        // P波容错：允许起始点向下一些（0.5格以内）
        private boolean pWaveStarted = false;
        private boolean pWaveHasGoneUp = false;
        
        // 记录各阶段的错误，最后统一显示
        private List<String> stageErrors;

        public ECGView(Context context) {
            super(context);
            init();
        }

        private void init() {
            gridPaint = new Paint();
            gridPaint.setColor(Color.parseColor("#FFB6C1"));
            gridPaint.setStrokeWidth(1);

            ecgPaint = new Paint();
            ecgPaint.setColor(Color.BLACK);
            ecgPaint.setStrokeWidth(3);
            ecgPaint.setStyle(Paint.Style.STROKE);

            activePaint = new Paint();
            activePaint.setColor(Color.RED);
            activePaint.setStrokeWidth(4);
            activePaint.setStyle(Paint.Style.STROKE);

            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(18); // 文字缩小

            startPointPaint = new Paint();
            startPointPaint.setColor(Color.GREEN);
            startPointPaint.setStrokeWidth(5);
            startPointPaint.setStyle(Paint.Style.FILL);
            
            drawnPath = new Path();
            currentPathPoints = new ArrayList<>();
            stageErrors = new ArrayList<>();
            updateStage(stageNames[0]);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            baselineY = h * 2f / 3f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawGrid(canvas);
            drawPWaveStartMarker(canvas);
            canvas.drawLine(0, baselineY, getWidth(), baselineY, ecgPaint);
            canvas.drawPath(drawnPath, activePaint);
            drawStageHint(canvas);
        }

        private void drawGrid(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            
            for (int x = 0; x < width; x += (int)smallGridSize) {
                canvas.drawLine(x, 0, x, height, gridPaint);
            }
            for (int y = 0; y < height; y += (int)smallGridSize) {
                canvas.drawLine(0, y, width, y, gridPaint);
            }
            
            Paint bigGridPaint = new Paint();
            bigGridPaint.setColor(Color.parseColor("#FF1493"));
            bigGridPaint.setStrokeWidth(2);
            
            for (int x = 0; x < width; x += (int)bigGridSize) {
                canvas.drawLine(x, 0, x, height, bigGridPaint);
            }
            for (int y = 0; y < height; y += (int)bigGridSize) {
                canvas.drawLine(0, y, width, y, bigGridPaint);
            }
        }

        private void drawPWaveStartMarker(Canvas canvas) {
            float markerX = smallGridSize * 2;
            float markerY = baselineY;
            
            float markerSize = smallGridSize * 0.6f;
            canvas.drawLine(markerX - markerSize/2, markerY, markerX + markerSize/2, markerY, startPointPaint);
            canvas.drawLine(markerX, markerY - markerSize/2, markerX, markerY + markerSize/2, startPointPaint);
            
            Paint markerTextPaint = new Paint();
            markerTextPaint.setColor(Color.GREEN);
            markerTextPaint.setTextSize(16);
            markerTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("P起点", markerX, markerY + markerSize + 25, markerTextPaint);
        }

        private void drawStageHint(Canvas canvas) {
            String hint = "";
            switch (currentStage) {
                case 0:
                    hint = "【P波】<3格(300px)，<2.5格(250px)，向上，回基线自动结算";
                    break;
                case 1:
                    hint = "【PR间期】水平，3-5格(300-500px)";
                    break;
                case 2:
                    hint = "【QRS】Q<1格→R>5格→S，总<3格";
                    break;
                case 3:
                    hint = "【ST段】回基线，压低<0.5格";
                    break;
                case 4:
                    hint = "【T波】向上，>R/10且<5格";
                    break;
            }
            canvas.drawText(hint, 30, 50, textPaint);
            
            // 显示已记录的异常
            if (!stageErrors.isEmpty()) {
                Paint errorPaint = new Paint();
                errorPaint.setColor(Color.RED);
                errorPaint.setTextSize(14);
                int y = getHeight() - 40;
                for (int i = stageErrors.size() - 1; i >= 0 && i >= stageErrors.size() - 3; i--) {
                    canvas.drawText("⚠ " + stageErrors.get(i), 30, y, errorPaint);
                    y -= 20;
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentPathPoints.clear();
                    pWaveStarted = false;
                    pWaveHasGoneUp = false;
                    
                    if (currentStage == 0) {
                        drawnPath.reset();
                        startX = smallGridSize * 2;
                        drawnPath.moveTo(startX, baselineY);
                        startPoint = new PointF(startX, baselineY);
                        lastX = startX;
                        lastY = baselineY;
                    } else {
                        if (Math.abs(x - lastX) > 100) {
                            stageErrors.add(stageNames[currentStage] + ": 应从上一阶段终点继续");
                            return true;
                        }
                        drawnPath.moveTo(lastX, lastY);
                        startPoint = new PointF(lastX, lastY);
                    }
                    currentPathPoints.add(new PointF(x, y));
                    return true;

                case MotionEvent.ACTION_MOVE:
                    drawnPath.lineTo(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    
                    // P波自动检测和结算
                    if (currentStage == 0) {
                        handlePWaveDrawing(x, y);
                    }
                    
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    endPoint = new PointF(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    
                    // 验证当前阶段（记录错误但不阻止）
                    validateCurrentStage();
                    
                    lastX = x;
                    lastY = y;
                    currentStage++;
                    
                    if (currentStage >= stageNames.length) {
                        showFinalResult();
                    } else {
                        updateStage(stageNames[currentStage]);
                        invalidate();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
        
        // P波容错绘制处理
        private void handlePWaveDrawing(float x, float y) {
            // 检测是否向上过（进入P波主体）
            if (!pWaveStarted) {
                // 容错：允许起始点向下最多0.5格
                if (y < baselineY + smallGridSize * 0.5f) {
                    pWaveStarted = true;
                }
            }
            
            if (pWaveStarted) {
                if (y < baselineY - smallGridSize * 0.3f) {
                    pWaveHasGoneUp = true;
                }
                
                // 向上后落回基线，自动结算
                if (pWaveHasGoneUp && y >= baselineY - smallGridSize * 0.3f) {
                    endPoint = new PointF(x, baselineY);
                    currentPathPoints.add(new PointF(x, baselineY));
                    validateCurrentStage();
                    
                    lastX = x;
                    lastY = baselineY;
                    currentStage++;
                    
                    post(() -> {
                        if (currentStage < stageNames.length) {
                            updateStage(stageNames[currentStage]);
                        } else {
                            showFinalResult();
                        }
                        invalidate();
                    });
                }
            }
        }

        private void validateCurrentStage() {
            float width = Math.abs(endPoint.x - startPoint.x);
            float maxHeight = 0;
            float minHeight = 0;
            
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxHeight) maxHeight = h;
                if (h < minHeight) minHeight = h;
            }
            
            float smallGridsX = width / smallGridSize;
            float smallGridsYUp = maxHeight / smallGridSize;
            float smallGridsYDown = Math.abs(minHeight) / smallGridSize;
            
            switch (currentStage) {
                case 0: // P波 - 容错模式
                    float pTime = smallGridsX * secondsPerSmallGrid;
                    pWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    
                    if (pTime > P_MAX_TIME) {
                        stageErrors.add("P波时间超标：" + String.format("%.2f", pTime) + "s (<0.12s)");
                    }
                    if (pWaveAmp > P_MAX_AMP) {
                        stageErrors.add("P波幅度超标：" + String.format("%.2f", pWaveAmp) + "mv (<0.25mv)");
                    }
                    // 容错：不要求必须向上，但记录
                    if (maxHeight < smallGridSize * 0.5f) {
                        stageErrors.add("P波幅度可能不足");
                    }
                    break;
                    
                case 1: // PR间期
                    float prTime = smallGridsX * secondsPerSmallGrid;
                    if (prTime < PR_MIN_TIME || prTime > PR_MAX_TIME) {
                        stageErrors.add("PR间期异常：" + String.format("%.2f", prTime) + "s (0.12-0.20s)");
                    }
                    if (Math.abs(endPoint.y - baselineY) > smallGridSize * 0.5) {
                        stageErrors.add("PR间期不在基线上");
                    }
                    break;
                    
                case 2: // QRS
                    float qrsTime = smallGridsX * secondsPerSmallGrid;
                    analyzeQRS();
                    
                    if (qrsTime > QRS_MAX_TIME) {
                        stageErrors.add("QRS时间超标：" + String.format("%.2f", qrsTime) + "s");
                    }
                    if (rWaveAmp < 0.5f) {
                        stageErrors.add("QRS振幅不足：R=" + String.format("%.2f", rWaveAmp) + "mv");
                    }
                    float qTime = detectQTime();
                    if (qTime > Q_MAX_TIME) {
                        stageErrors.add("Q波时间超标");
                    }
                    if (qWaveAmp > 0 && rWaveAmp > 0 && (qWaveAmp / rWaveAmp) > Q_MAX_RATIO) {
                        stageErrors.add("Q波幅度超标");
                    }
                    break;
                    
                case 3: // ST段
                    analyzeSTSegment();
                    if (maxHeight > smallGridSize && isConvexUpward()) {
                        stageErrors.add("ST段弓背上抬(异常)");
                    }
                    float stDev = smallGridsYUp * mvPerSmallGrid;
                    if (stDev > ST_ELEVATION_MAX) {
                        stageErrors.add("ST段抬高超标");
                    }
                    float stDep = smallGridsYDown * mvPerSmallGrid;
                    if (stDep > ST_DEPRESSION_MAX) {
                        stageErrors.add("ST段压低超标");
                    }
                    break;
                    
                case 4: // T波
                    analyzeTWave();
                    tWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    if (tWaveAmp > T_MAX_AMP) {
                        stageErrors.add("T波幅度超标");
                    }
                    if (rWaveAmp > 0 && (tWaveAmp / rWaveAmp) < T_MIN_RATIO) {
                        stageErrors.add("T波幅度不足(T<R/10)");
                    }
                    if (endPoint.y > baselineY - smallGridSize) {
                        stageErrors.add("T波应向上");
                    }
                    break;
            }
        }

        private void showFinalResult() {
            if (stageErrors.isEmpty()) {
                post(() -> MainActivity.this.showFinalResult(true, 
                    "所有波形参数均在正常范围内！\n\n" +
                    "• P波：时限<0.12s，幅度<0.25mv\n" +
                    "• PR间期：0.12-0.20s\n" +
                    "• QRS：Q<1/4R，时限<0.12s\n" +
                    "• ST段：无异常抬高\n" +
                    "• T波：幅度合适，方向正确"));
            } else {
                StringBuilder sb = new StringBuilder("发现以下异常：\n\n");
                for (int i = 0; i < stageErrors.size(); i++) {
                    sb.append((i+1) + ". " + stageErrors.get(i) + "\n");
                }
                sb.append("\n建议对照标准重新练习。");
                post(() -> MainActivity.this.showFinalResult(false, sb.toString()));
            }
        }

        private void analyzeQRS() {
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            PointF minPoint = null;
            PointF maxPoint = null;
            
            for (PointF p : currentPathPoints) {
                if (p.y < minY) { minY = p.y; minPoint = p; }
                if (p.y > maxY) { maxY = p.y; maxPoint = p; }
            }
            
            if (maxPoint != null && maxPoint.y < baselineY) {
                rWaveAmp = (baselineY - maxPoint.y) / smallGridSize * mvPerSmallGrid;
            }
            if (minPoint != null && minPoint.y > baselineY) {
                float downAmp = (minPoint.y - baselineY) / smallGridSize * mvPerSmallGrid;
                if (minPoint.x < startPoint.x + (endPoint.x - startPoint.x) * 0.3) {
                    qWaveAmp = downAmp;
                } else {
                    sWaveAmp = downAmp;
                }
            }
        }

        private float detectQTime() {
            float qStart = 0, qEnd = 0;
            boolean inQ = false;
            for (PointF p : currentPathPoints) {
                if (p.y > baselineY + smallGridSize * 0.3) {
                    if (!inQ) { inQ = true; qStart = p.x; }
                    qEnd = p.x;
                } else if (inQ && p.y < baselineY) break;
            }
            return (qEnd - qStart) / smallGridSize * secondsPerSmallGrid;
        }

        private void analyzeSTSegment() {}

        private void analyzeTWave() {
            float maxT = 0;
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxT) maxT = h;
            }
            tWaveAmp = maxT / smallGridSize * mvPerSmallGrid;
        }

        private boolean isConvexUpward() {
            if (currentPathPoints.size() < 3) return false;
            int midIndex = currentPathPoints.size() / 2;
            PointF midPoint = currentPathPoints.get(midIndex);
            PointF start = currentPathPoints.get(0);
            PointF end = currentPathPoints.get(currentPathPoints.size() - 1);
            float lineYAtMid = start.y + (end.y - start.y) * 0.5f;
            return midPoint.y > lineYAtMid + smallGridSize * 0.5;
        }

        public void reset() {
            drawnPath.reset();
            currentPathPoints.clear();
            stageErrors.clear();
            currentStage = 0;
            lastX = 0;
            lastY = 0;
            pWaveAmp = 0;
            qWaveAmp = 0;
            rWaveAmp = 0;
            sWaveAmp = 0;
            tWaveAmp = 0;
            pWaveStarted = false;
            pWaveHasGoneUp = false;
            updateStage(stageNames[0]);
            updateStatus("就绪");
            invalidate();
        }
    }
}
