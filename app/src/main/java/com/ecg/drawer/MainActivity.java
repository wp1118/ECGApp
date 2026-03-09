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
    private TextView tvStage;
    private Button btnReset;
    private Button btnGuide;
    private Button btnContact;
    private Button btnEvaluate;
    private TextView tvCopyright;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        tvStage = findViewById(R.id.tvStage);
        btnReset = findViewById(R.id.btnReset);
        btnGuide = findViewById(R.id.btnGuide);
        btnContact = findViewById(R.id.btnContact);
        btnEvaluate = findViewById(R.id.btnEvaluate);
        tvCopyright = findViewById(R.id.tvCopyright);

        LinearLayout container = findViewById(R.id.ecgContainer);
        ecgView = new ECGView(this);
        container.addView(ecgView);

        btnReset.setOnClickListener(v -> ecgView.reset());
        btnGuide.setOnClickListener(v -> showGuide());
        btnContact.setOnClickListener(v -> showContactDialog());
        btnEvaluate.setOnClickListener(v -> ecgView.showCurrentEvaluation());
        tvCopyright.setOnClickListener(v -> ecgView.showCurrentEvaluation());
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
        return "【P波】<0.12s(3格)，<0.25mv(2.5格)，向上圆钝\n\n" +
               "【PR间期】0.12-0.20s(3-5格)，水平于基线\n\n" +
               "【QRS】Q<0.04s(1格)，Q<1/4R，R>0.5mv，总<0.12s\n\n" +
               "【ST段】回到基线，压低<0.05mv，无弓背上抬\n\n" +
               "【T波】向上圆钝，>R/10且<0.5mv";
    }

    private String getContactText() {
        return "本软件乃捐赠版（Donationware），版权归Sean & Dean所有，专为医林同道、杏林学子备之。\n\n" +
               "若君用得顺手，愿赏几文润笔，助我等勉力改进，则感激涕零，叩首以谢！\n\n" +
               "赏银随意，不拘多寡，长按下方玄机之码，微信支付宝皆可通也。\n\n" +
               "如有高见或疑问，欢迎传书至：wangdgj@qq.com\n\n" +
               "多谢诸位！";
    }

    public void updateStage(String stage) {
        tvStage.setText("当前：" + stage);
    }

    public void showEvaluationResult(boolean success, String message) {
        new AlertDialog.Builder(this)
            .setTitle(success ? "评价结果" : "有异常")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }

    class ECGView extends View {
        private Paint gridPaint;
        private Paint ecgPaint;
        private Paint activePaint;
        private Paint textPaint;
        private Paint startPointPaint;
        
        private float smallGridSize = 60f;
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
        private final float R_MIN_AMP = 0.5f;
        private final float ST_ELEVATION_MAX = 0.1f;
        private final float ST_DEPRESSION_MAX = 0.05f;
        private final float T_MAX_AMP = 0.5f;
        private final float T_MIN_RATIO = 0.1f;
        
        private float secondsPerSmallGrid = 0.04f;
        private float mvPerSmallGrid = 0.1f;
        
        private boolean pWaveStarted = false;
        private boolean pWaveHasGoneUp = false;
        
        private List<String> stageErrors;
        private boolean[] stageCompleted;

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
            textPaint.setTextSize(16);

            startPointPaint = new Paint();
            startPointPaint.setColor(Color.GREEN);
            startPointPaint.setStrokeWidth(5);
            startPointPaint.setStyle(Paint.Style.FILL);
            
            drawnPath = new Path();
            currentPathPoints = new ArrayList<>();
            stageErrors = new ArrayList<>();
            stageCompleted = new boolean[5];
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
                    hint = "【P波】<3格(180px)，<2.5格(150px)，向上圆钝，回基线结算";
                    break;
                case 1:
                    hint = "【PR间期】水平于基线，3-5格(180-300px)";
                    break;
                case 2:
                    hint = "【QRS】Q<1格，R>5格，Q<1/4R，总<3格";
                    break;
                case 3:
                    hint = "【ST段】回到基线，压低<0.5格，无弓背上抬";
                    break;
                case 4:
                    hint = "【T波】向上圆钝，>R/10且<5格";
                    break;
            }
            canvas.drawText(hint, 30, 50, textPaint);
            
            if (!stageErrors.isEmpty()) {
                Paint errorPaint = new Paint();
                errorPaint.setColor(Color.RED);
                errorPaint.setTextSize(13);
                int y = getHeight() - 35;
                for (int i = stageErrors.size() - 1; i >= 0 && i >= stageErrors.size() - 3; i--) {
                    canvas.drawText("⚠ " + stageErrors.get(i), 30, y, errorPaint);
                    y -= 18;
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
                        if (Math.abs(x - lastX) > 50) {
                            stageErrors.add(stageNames[currentStage] + ": 未从上一阶段终点连接");
                        }
                        drawnPath.moveTo(lastX, lastY);
                        startPoint = new PointF(lastX, lastY);
                    }
                    currentPathPoints.add(new PointF(x, y));
                    return true;

                case MotionEvent.ACTION_MOVE:
                    drawnPath.lineTo(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    
                    if (currentStage == 0) {
                        handlePWaveDrawing(x, y);
                    }
                    
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    endPoint = new PointF(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    
                    validateCurrentStage();
                    stageCompleted[currentStage] = true;
                    
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
        
        private void handlePWaveDrawing(float x, float y) {
            if (!pWaveStarted) {
                if (y < baselineY + smallGridSize * 0.5f) {
                    pWaveStarted = true;
                }
            }
            
            if (pWaveStarted) {
                if (y < baselineY - smallGridSize * 0.3f) {
                    pWaveHasGoneUp = true;
                }
                
                if (pWaveHasGoneUp && y >= baselineY - smallGridSize * 0.3f) {
                    endPoint = new PointF(x, baselineY);
                    currentPathPoints.add(new PointF(x, baselineY));
                    validateCurrentStage();
                    stageCompleted[0] = true;
                    
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
                case 0: // P波 - 增强形态检测
                    float pTime = smallGridsX * secondsPerSmallGrid;
                    pWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    
                    // 时间检测
                    if (pTime > P_MAX_TIME + 0.01f) {
                        stageErrors.add("P波时间过长：" + String.format("%.3f", pTime) + "s (应<0.12s)");
                    } else if (pTime > P_MAX_TIME) {
                        stageErrors.add("P波时间略超：" + String.format("%.3f", pTime) + "s (临界)");
                    }
                    
                    // 幅度检测
                    if (pWaveAmp > P_MAX_AMP + 0.05f) {
                        stageErrors.add("P波幅度超标：" + String.format("%.3f", pWaveAmp) + "mv (应<0.25mv)");
                    } else if (pWaveAmp > P_MAX_AMP) {
                        stageErrors.add("P波幅度略超：" + String.format("%.3f", pWaveAmp) + "mv (临界)");
                    }
                    
                    // 形态检测 - 必须向上
                    if (maxHeight < smallGridSize * 0.5f) {
                        stageErrors.add("P波形态异常：幅度过小(应向上圆钝)");
                    }
                    
                    // 检测P波是否向下（异常）
                    if (minHeight > smallGridSize * 0.3f) {
                        stageErrors.add("P波形态异常：出现明显负向波");
                    }
                    break;
                    
                case 1: // PR间期 - 增强水平检测
                    float prTime = smallGridsX * secondsPerSmallGrid;
                    float prDeviation = Math.abs(endPoint.y - baselineY);
                    
                    // 时间严格检测
                    if (prTime < PR_MIN_TIME - 0.02f) {
                        stageErrors.add("PR间期过短：" + String.format("%.3f", prTime) + "s (应≥0.12s)");
                    } else if (prTime < PR_MIN_TIME) {
                        stageErrors.add("PR间期偏短：" + String.format("%.3f", prTime) + "s (接近下限)");
                    } else if (prTime > PR_MAX_TIME + 0.02f) {
                        stageErrors.add("PR间期过长：" + String.format("%.3f", prTime) + "s (应≤0.20s)");
                    } else if (prTime > PR_MAX_TIME) {
                        stageErrors.add("PR间期偏长：" + String.format("%.3f", prTime) + "s (接近上限)");
                    }
                    
                    // 水平度严格检测
                    if (prDeviation > smallGridSize * 0.8f) {
                        stageErrors.add("PR间期不水平：偏离基线" + String.format("%.1f", prDeviation/smallGridSize) + "格(应≤0.5格)");
                    } else if (prDeviation > smallGridSize * 0.5f) {
                        stageErrors.add("PR间期略偏斜：偏离基线" + String.format("%.1f", prDeviation/smallGridSize) + "格");
                    }
                    
                    // 检测PR段是否有明显波动
                    float prMaxDev = 0;
                    for (PointF p : currentPathPoints) {
                        float dev = Math.abs(p.y - baselineY);
                        if (dev > prMaxDev) prMaxDev = dev;
                    }
                    if (prMaxDev > smallGridSize * 1.0f) {
                        stageErrors.add("PR间期形态异常：有明显波动");
                    }
                    break;
                    
                case 2: // QRS - 增强形态和时间检测
                    float qrsTime = smallGridsX * secondsPerSmallGrid;
                    analyzeQRS();
                    
                    // 时间严格检测
                    if (qrsTime > QRS_MAX_TIME + 0.01f) {
                        stageErrors.add("QRS时间超标：" + String.format("%.3f", qrsTime) + "s (应<0.12s)");
                    } else if (qrsTime > QRS_MAX_TIME) {
                        stageErrors.add("QRS时间临界：" + String.format("%.3f", qrsTime) + "s (接近上限)");
                    }
                    
                    // R波振幅检测
                    if (rWaveAmp < R_MIN_AMP - 0.1f) {
                        stageErrors.add("R波振幅不足：" + String.format("%.3f", rWaveAmp) + "mv (应≥0.5mv)");
                    } else if (rWaveAmp < R_MIN_AMP) {
                        stageErrors.add("R波振幅偏小：" + String.format("%.3f", rWaveAmp) + "mv (接近下限)");
                    }
                    
                    // Q波检测
                    float qTime = detectQTime();
                    if (qTime > Q_MAX_TIME + 0.01f) {
                        stageErrors.add("Q波时间过长：" + String.format("%.3f", qTime) + "s (应<0.04s)");
                    } else if (qTime > Q_MAX_TIME) {
                        stageErrors.add("Q波时间临界：" + String.format("%.3f", qTime) + "s");
                    }
                    
                    // Q/R比值严格检测
                    if (qWaveAmp > 0 && rWaveAmp > 0) {
                        float qRratio = qWaveAmp / rWaveAmp;
                        if (qRratio > Q_MAX_RATIO + 0.05f) {
                            stageErrors.add("Q波幅度超标：Q/R=" + String.format("%.2f", qRratio) + "(应<0.25)");
                        } else if (qRratio > Q_MAX_RATIO) {
                            stageErrors.add("Q波幅度临界：Q/R=" + String.format("%.2f", qRratio) + "(接近上限)");
                        }
                    }
                    
                    // QRS结构检测
                    if (rWaveAmp <= 0.1f) {
                        stageErrors.add("QRS形态异常：未检测到明显R波");
                    }
                    break;
                    
                case 3: // ST段 - 增强形态检测
                    analyzeSTSegment();
                    
                    // 弓背上抬检测
                    boolean isElevated = maxHeight > smallGridSize * 0.3f;
                    boolean isConvex = isConvexUpward();
                    
                    if (isElevated && isConvex) {
                        stageErrors.add("ST段弓背上抬：急性心梗表现(严重异常)");
                    } else if (isConvex && maxHeight > smallGridSize * 0.2f) {
                        stageErrors.add("ST段形态可疑：轻度弓背改变");
                    }
                    
                    // 抬高严格检测
                    float stDev = smallGridsYUp * mvPerSmallGrid;
                    if (stDev > ST_ELEVATION_MAX + 0.02f) {
                        stageErrors.add("ST段抬高超标：" + String.format("%.3f", stDev) + "mv (应<0.1mv)");
                    } else if (stDev > ST_ELEVATION_MAX) {
                        stageErrors.add("ST段抬高临界：" + String.format("%.3f", stDev) + "mv");
                    }
                    
                    // 压低严格检测
                    float stDep = smallGridsYDown * mvPerSmallGrid;
                    if (stDep > ST_DEPRESSION_MAX + 0.02f) {
                        stageErrors.add("ST段压低超标：" + String.format("%.3f", stDep) + "mv (应<0.05mv)");
                    } else if (stDep > ST_DEPRESSION_MAX) {
                        stageErrors.add("ST段压低临界：" + String.format("%.3f", stDep) + "mv");
                    }
                    
                    // ST段是否回到基线
                    float stEndDev = Math.abs(endPoint.y - baselineY);
                    if (stEndDev > smallGridSize * 0.5f) {
                        stageErrors.add("ST段未回基线：终点偏离" + String.format("%.1f", stEndDev/smallGridSize) + "格");
                    }
                    break;
                    
                case 4: // T波 - 增强振幅和形态检测
                    analyzeTWave();
                    tWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    
                    // 振幅上限严格检测
                    if (tWaveAmp > T_MAX_AMP + 0.05f) {
                        stageErrors.add("T波幅度超标：" + String.format("%.3f", tWaveAmp) + "mv (应<0.5mv)");
                    } else if (tWaveAmp > T_MAX_AMP) {
                        stageErrors.add("T波幅度临界：" + String.format("%.3f", tWaveAmp) + "mv (接近上限)");
                    }
                    
                    // T/R比值严格检测
                    if (rWaveAmp > 0) {
                        float trRatio = tWaveAmp / rWaveAmp;
                        if (trRatio < T_MIN_RATIO - 0.03f) {
                            stageErrors.add("T波幅度不足：T/R=" + String.format("%.2f", trRatio) + "(应>0.1)");
                        } else if (trRatio < T_MIN_RATIO) {
                            stageErrors.add("T波幅度偏小：T/R=" + String.format("%.2f", trRatio) + "(接近下限)");
                        }
                    }
                    
                    // T波方向检测
                    if (endPoint.y > baselineY - smallGridSize * 0.5f) {
                        stageErrors.add("T波方向异常：应直立向上");
                    }
                    
                    // T波形态检测（圆钝）
                    if (!isTWaveRound()) {
                        stageErrors.add("T波形态异常：应圆钝对称");
                    }
                    break;
            }
        }

        private boolean isTWaveRound() {
            if (currentPathPoints.size() < 5) return true;
            
            // 检测T波是否对称圆钝
            int peakIndex = 0;
            float maxH = 0;
            for (int i = 0; i < currentPathPoints.size(); i++) {
                float h = baselineY - currentPathPoints.get(i).y;
                if (h > maxH) {
                    maxH = h;
                    peakIndex = i;
                }
            }
            
            // 检测上升和下降是否对称
            int leftCount = peakIndex;
            int rightCount = currentPathPoints.size() - peakIndex - 1;
            float ratio = (float) Math.min(leftCount, rightCount) / Math.max(leftCount, rightCount);
            
            return ratio > 0.4f; // 允许一定不对称，但不要太离谱
        }

        public void showCurrentEvaluation() {
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0; i < stageNames.length; i++) {
                if (stageCompleted[i]) {
                    sb.append("【" + stageNames[i] + "】");
                    boolean hasError = false;
                    for (String err : stageErrors) {
                        if (err.startsWith(stageNames[i])) {
                            sb.append(" ✗\n  " + err.substring(stageNames[i].length() + 1) + "\n");
                            hasError = true;
                        }
                    }
                    if (!hasError) {
                        sb.append(" ✓ 正常\n");
                    }
                } else if (i == currentStage) {
                    sb.append("【" + stageNames[i] + "】绘制中...\n");
                } else {
                    sb.append("【" + stageNames[i] + "】未开始\n");
                }
            }
            
            if (stageErrors.isEmpty() && currentStage >= stageNames.length) {
                post(() -> MainActivity.this.showEvaluationResult(true, "所有波形参数均在正常范围内！\n\n" + sb.toString()));
            } else {
                post(() -> MainActivity.this.showEvaluationResult(false, sb.toString()));
            }
        }

        private void showFinalResult() {
            if (stageErrors.isEmpty()) {
                post(() -> MainActivity.this.showEvaluationResult(true, 
                    "所有波形参数均在正常范围内！\n\n" +
                    "• P波：时限<0.12s，幅度<0.25mv，向上圆钝\n" +
                    "• PR间期：0.12-0.20s，水平于基线\n" +
                    "• QRS：Q<0.04s，Q<1/4R，R>0.5mv，总<0.12s\n" +
                    "• ST段：回到基线，无异常抬高\n" +
                    "• T波：幅度合适，方向正确"));
            } else {
                StringBuilder sb = new StringBuilder("发现以下异常：\n\n");
                for (int i = 0; i < stageErrors.size(); i++) {
                    sb.append((i+1) + ". " + stageErrors.get(i) + "\n");
                }
                sb.append("\n建议对照标准重新练习。");
                post(() -> MainActivity.this.showEvaluationResult(false, sb.toString()));
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
            for (int i = 0; i < stageCompleted.length; i++) {
                stageCompleted[i] = false;
            }
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
            invalidate();
        }
    }
}
