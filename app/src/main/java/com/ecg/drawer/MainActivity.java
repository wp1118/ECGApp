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
        return "【P波】时限<0.12s，振幅<0.25mV，向上圆钝\n\n" +
               "【PR间期】0.12-0.20s，P波终点至QRS起点\n\n" +
               "【QRS】Q<0.04s，Q<1/4R，R>0.5mV，时限<0.12s\n\n" +
               "【ST段】J点后测量，压低>0.05mV为异常，区分水平型/上斜型\n\n" +
               "【T波】振幅>0.2mV且>R/10，<0.5mV，向上圆钝";
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
        private Paint markerPaint;
        
        private float smallGridSize = 60f;
        private float bigGridSize = smallGridSize * 5;
        
        private Path drawnPath;
        private List<PointF> currentPathPoints;
        private List<PointF> allPathPoints;
        private float lastX = 0;
        private float lastY = 0;
        private float baselineY = 0;
        private float startX = 100;
        
        private int currentStage = 0;
        private String[] stageNames = {"P波", "PR间期", "QRS波群", "ST段", "T波"};
        private PointF startPoint;
        private PointF endPoint;
        
        // 波形关键点
        private PointF pWaveEnd;
        private PointF qrsStart;
        private PointF qrsEnd;
        private PointF jPoint;
        private PointF tWaveStart;
        private PointF tWaveEnd;
        
        private float pWaveAmp = 0;
        private float qWaveAmp = 0;
        private float rWaveAmp = 0;
        private float sWaveAmp = 0;
        private float tWaveAmp = 0;
        private float prInterval = 0;
        private float qrsDuration = 0;
        private float qtInterval = 0;
        
        // 标准值
        private final float P_MAX_TIME = 0.12f;
        private final float P_MAX_AMP = 0.25f;
        private final float PR_MIN_TIME = 0.12f;
        private final float PR_MAX_TIME = 0.20f;
        private final float Q_MAX_TIME = 0.04f;
        private final float Q_MAX_RATIO = 0.25f;
        private final float QRS_MAX_TIME = 0.12f;
        private final float R_MIN_AMP = 0.5f;
        private final float ST_DEPRESSION_THRESHOLD = 0.05f;
        private final float T_MIN_AMP = 0.2f;
        private final float T_MAX_AMP = 0.5f;
        private final float T_MIN_RATIO = 0.1f;
        
        private float secondsPerSmallGrid = 0.04f;
        private float mvPerSmallGrid = 0.1f;
        
        private boolean pWaveStarted = false;
        private boolean pWaveHasGoneUp = false;
        
        private List<String> stageErrors;
        private List<String> detailedAnalysis;
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
            
            markerPaint = new Paint();
            markerPaint.setColor(Color.BLUE);
            markerPaint.setStrokeWidth(4);
            markerPaint.setStyle(Paint.Style.STROKE);
            
            drawnPath = new Path();
            currentPathPoints = new ArrayList<>();
            allPathPoints = new ArrayList<>();
            stageErrors = new ArrayList<>();
            detailedAnalysis = new ArrayList<>();
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
            drawKeyPoints(canvas);
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
                canvas.drawLine(0, y, width, y, gridGridSize);
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

        private void drawKeyPoints(Canvas canvas) {
            // 绘制检测到的关键点标记
            if (pWaveEnd != null) {
                canvas.drawCircle(pWaveEnd.x, pWaveEnd.y, 8, markerPaint);
            }
            if (jPoint != null) {
                canvas.drawCircle(jPoint.x, jPoint.y, 8, markerPaint);
            }
        }

        private void drawStageHint(Canvas canvas) {
            String hint = "";
            switch (currentStage) {
                case 0:
                    hint = "【P波】时限<0.12s，振幅<0.25mV，向上圆钝";
                    break;
                case 1:
                    hint = "【PR段】P波终点→QRS起点，水平于基线";
                    break;
                case 2:
                    hint = "【QRS】精确检测起点/终点，Q<0.04s，R>0.5mV";
                    break;
                case 3:
                    hint = "【ST段】J点后测量，检测0.05mV以上压低";
                    break;
                case 4:
                    hint = "【T波】检测低平/倒置，振幅>0.2mV且>R/10";
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
                        allPathPoints.clear();
                        startX = smallGridSize * 2;
                        drawnPath.moveTo(startX, baselineY);
                        startPoint = new PointF(startX, baselineY);
                        lastX = startX;
                        lastY = baselineY;
                    } else {
                        drawnPath.moveTo(lastX, lastY);
                        startPoint = new PointF(lastX, lastY);
                    }
                    currentPathPoints.add(new PointF(x, y));
                    allPathPoints.add(new PointF(x, y));
                    return true;

                case MotionEvent.ACTION_MOVE:
                    drawnPath.lineTo(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    allPathPoints.add(new PointF(x, y));
                    
                    if (currentStage == 0) {
                        handlePWaveDrawing(x, y);
                    }
                    
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    endPoint = new PointF(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    allPathPoints.add(new PointF(x, y));
                    
                    // 执行高级波形分析
                    analyzeWaveformAdvanced();
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
                    // P波终点检测 - 回到基线
                    pWaveEnd = new PointF(x, baselineY);
                    endPoint = pWaveEnd;
                    currentPathPoints.add(pWaveEnd);
                    
                    analyzeWaveformAdvanced();
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

        // 高级波形分析算法
        private void analyzeWaveformAdvanced() {
            if (currentPathPoints.size() < 2) return;
            
            switch (currentStage) {
                case 0:
                    analyzePWaveAdvanced();
                    break;
                case 1:
                    analyzePRSegmentAdvanced();
                    break;
                case 2:
                    analyzeQRSAdvanced();
                    break;
                case 3:
                    analyzeSTSegmentAdvanced();
                    break;
                case 4:
                    analyzeTWaveAdvanced();
                    break;
            }
        }

        // P波高级分析 - 精确检测形态
        private void analyzePWaveAdvanced() {
            float width = Math.abs(endPoint.x - startPoint.x);
            float maxHeight = 0;
            float minHeight = 0;
            
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxHeight) maxHeight = h;
                if (h < minHeight) minHeight = h;
            }
            
            float pTime = (width / smallGridSize) * secondsPerSmallGrid;
            pWaveAmp = (maxHeight / smallGridSize) * mvPerSmallGrid;
            
            detailedAnalysis.add("P波时限: " + String.format("%.3f", pTime) + "s");
            detailedAnalysis.add("P波振幅: " + String.format("%.3f", pWaveAmp) + "mV");
            
            // 精确时间检测
            if (pTime > P_MAX_TIME) {
                stageErrors.add("P波时限超标: " + String.format("%.3f", pTime) + "s (应<0.12s)");
            }
            
            // 精确振幅检测
            if (pWaveAmp > P_MAX_AMP) {
                stageErrors.add("P波振幅超标: " + String.format("%.3f", pWaveAmp) + "mV (应<0.25mV)");
            }
            
            // 形态检测 - 必须向上圆钝
            if (maxHeight < smallGridSize * 0.3f) {
                stageErrors.add("P波形态异常: 振幅过小(应向上)");
            }
            
            // 检测负向成分
            if (minHeight > smallGridSize * 0.2f) {
                stageErrors.add("P波形态异常: 存在明显负向波");
            }
            
            // 记录P波终点用于PR间期计算
            pWaveEnd = endPoint;
        }

        // PR段高级分析
        private void analyzePRSegmentAdvanced() {
            float width = Math.abs(endPoint.x - startPoint.x);
            prInterval = (width / smallGridSize) * secondsPerSmallGrid;
            
            detailedAnalysis.add("PR间期: " + String.format("%.3f", prInterval) + "s");
            
            // 精确时间范围检测
            if (prInterval < PR_MIN_TIME) {
                stageErrors.add("PR间期过短: " + String.format("%.3f", prInterval) + "s (应≥0.12s)");
            } else if (prInterval > PR_MAX_TIME) {
                stageErrors.add("PR间期过长: " + String.format("%.3f", prInterval) + "s (应≤0.20s)");
            }
            
            // 水平度检测 - 计算路径偏离基线的程度
            double totalDeviation = 0;
            double maxDeviation = 0;
            for (PointF p : currentPathPoints) {
                double dev = Math.abs(p.y - baselineY);
                totalDeviation += dev;
                if (dev > maxDeviation) maxDeviation = dev;
            }
            double avgDeviation = totalDeviation / currentPathPoints.size();
            
            detailedAnalysis.add("PR段平均偏离: " + String.format("%.2f", avgDeviation/smallGridSize) + "格");
            
            if (maxDeviation > smallGridSize * 0.8f) {
                stageErrors.add("PR段不水平: 最大偏离" + String.format("%.1f", maxDeviation/smallGridSize) + "格");
            } else if (avgDeviation > smallGridSize * 0.3f) {
                stageErrors.add("PR段略偏斜: 平均偏离" + String.format("%.1f", avgDeviation/smallGridSize) + "格");
            }
        }

        // QRS高级分析 - 精确边界检测
        private void analyzeQRSAdvanced() {
            // 检测QRS波群的精确起点和终点
            detectQRSBoundaries();
            
            float width = Math.abs(endPoint.x - startPoint.x);
            qrsDuration = (width / smallGridSize) * secondsPerSmallGrid;
            
            // 分析QRS各成分
            analyzeQRSComponents();
            
            detailedAnalysis.add("QRS时限: " + String.format("%.3f", qrsDuration) + "s");
            detailedAnalysis.add("R波振幅: " + String.format("%.3f", rWaveAmp) + "mV");
            
            // 时限检测
            if (qrsDuration > QRS_MAX_TIME) {
                stageErrors.add("QRS时限超标: " + String.format("%.3f", qrsDuration) + "s (应<0.12s)");
            }
            
            // R波振幅
            if (rWaveAmp < R_MIN_AMP) {
                stageErrors.add("R波振幅不足: " + String.format("%.3f", rWaveAmp) + "mV (应≥0.5mV)");
            }
            
            // Q波分析
            if (qWaveAmp > 0) {
                float qTime = detectQTimeAdvanced();
                detailedAnalysis.add("Q波时限: " + String.format("%.3f", qTime) + "s");
                
                if (qTime > Q_MAX_TIME) {
                    stageErrors.add("Q波时限超标: " + String.format("%.3f", qTime) + "s");
                }
                
                if (rWaveAmp > 0) {
                    float qRratio = qWaveAmp / rWaveAmp;
                    if (qRratio > Q_MAX_RATIO) {
                        stageErrors.add("Q波幅度超标: Q/R=" + String.format("%.2f", qRratio));
                    }
                }
            }
            
            // 标记J点
            jPoint = endPoint;
        }

        // 精确检测QRS边界
        private void detectQRSBoundaries() {
            // QRS起点 - 从基线开始的偏离点
            for (int i = 0; i < currentPathPoints.size(); i++) {
                PointF p = currentPathPoints.get(i);
                if (Math.abs(p.y - baselineY) > smallGridSize * 0.2f) {
                    qrsStart = p;
                    break;
                }
            }
            
            // QRS终点 - 回到基线的点
            for (int i = currentPathPoints.size() - 1; i >= 0; i--) {
                PointF p = currentPathPoints.get(i);
                if (Math.abs(p.y - baselineY) > smallGridSize * 0.2f) {
                    qrsEnd = currentPathPoints.get(Math.min(i + 1, currentPathPoints.size() - 1));
                    break;
                }
            }
        }

        // 分析QRS各成分
        private void analyzeQRSComponents() {
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            PointF minPoint = null;
            PointF maxPoint = null;
            
            for (PointF p : currentPathPoints) {
                if (p.y < minY) { minY = p.y; minPoint = p; }
                if (p.y > maxY) { maxY = p.y; maxPoint = p; }
            }
            
            // R波 (最高点，向上)
            if (maxPoint != null && maxPoint.y < baselineY) {
                rWaveAmp = (baselineY - maxPoint.y) / smallGridSize * mvPerSmallGrid;
            }
            
            // Q波和S波 (最低点，向下)
            if (minPoint != null && minPoint.y > baselineY) {
                float downAmp = (minPoint.y - baselineY) / smallGridSize * mvPerSmallGrid;
                // 根据位置判断是Q波还是S波
                if (qrsStart != null && minPoint.x < qrsStart.x + (endPoint.x - qrsStart.x) * 0.4f) {
                    qWaveAmp = downAmp;
                } else {
                    sWaveAmp = downAmp;
                }
            }
        }

        // 精确Q波时间检测
        private float detectQTimeAdvanced() {
            if (qrsStart == null) return 0;
            
            float qStart = qrsStart.x;
            float qEnd = qStart;
            boolean inQ = false;
            
            for (PointF p : currentPathPoints) {
                if (p.y > baselineY + smallGridSize * 0.3f) {
                    if (!inQ) {
                        inQ = true;
                        qStart = p.x;
                    }
                    qEnd = p.x;
                } else if (inQ && p.y < baselineY) {
                    break;
                }
            }
            
            return ((qEnd - qStart) / smallGridSize) * secondsPerSmallGrid;
        }

        // ST段高级分析 - 敏感检测轻度压低
        private void analyzeSTSegmentAdvanced() {
            if (jPoint == null) return;
            
            // 在J点后40ms(1小格)处测量ST段
            float jPlus40msX = jPoint.x + smallGridSize;
            float stLevelAtJ40 = interpolateYAtX(jPlus40msX);
            float stDeviation = (stLevelAtJ40 - baselineY) / smallGridSize * mvPerSmallGrid;
            
            detailedAnalysis.add("J点后40ms ST段: " + String.format("%.3f", stDeviation) + "mV");
            
            // 敏感检测0.05mV以上的压低
            if (stDeviation > ST_DEPRESSION_THRESHOLD) {
                // ST段压低 - 进一步分类形态
                String morphology = classifySTMorphology();
                
                if (stDeviation > 0.1f) {
                    stageErrors.add("ST段明显压低: " + String.format("%.3f", stDeviation) + "mV, " + morphology);
                } else {
                    stageErrors.add("ST段轻度压低: " + String.format("%.3f", stDeviation) + "mV, " + morphology);
                }
            }
            
            // 检测ST段是否回到基线
            float endDeviation = (endPoint.y - baselineY) / smallGridSize * mvPerSmallGrid;
            if (Math.abs(endDeviation) > 0.5f) {
                stageErrors.add("ST段终点未回基线: 偏离" + String.format("%.2f", endDeviation) + "mV");
            }
        }

        // ST段形态分类 - 水平型 vs 上斜型
        private String classifySTMorphology() {
            // 测量ST段前半和后半的斜率
            int midIndex = currentPathPoints.size() / 2;
            if (midIndex < 2) return "无法分类";
            
            PointF pStart = currentPathPoints.get(0);
            PointF pMid = currentPathPoints.get(midIndex);
            PointF pEnd = currentPathPoints.get(currentPathPoints.size() - 1);
            
            float slope1 = (pMid.y - pStart.y) / (pMid.x - pStart.x + 0.001f);
            float slope2 = (pEnd.y - pMid.y) / (pEnd.x - pMid.x + 0.001f);
            
            float avgSlope = Math.abs((slope1 + slope2) / 2);
            
            if (avgSlope < 0.1f) {
                return "水平型压低";
            } else if (slope2 > slope1) {
                return "上斜型压低";
            } else {
                return "下斜型压低";
            }
        }

        // T波高级分析 - 检测低平和倒置
        private void analyzeTWaveAdvanced() {
            float maxHeight = 0;
            float minHeight = 0;
            PointF tPeak = null;
            
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxHeight) {
                    maxHeight = h;
                    tPeak = p;
                }
                if (h < minHeight) minHeight = h;
            }
            
            tWaveAmp = (maxHeight / smallGridSize) * mvPerSmallGrid;
            
            detailedAnalysis.add("T波振幅: " + String.format("%.3f", tWaveAmp) + "mV");
            
            // T波终点检测用于QT间期
            tWaveEnd = detectTWaveEnd();
            if (tWaveEnd != null && qrsStart != null) {
                qtInterval = ((tWaveEnd.x - qrsStart.x) / smallGridSize) * secondsPerSmallGrid;
                detailedAnalysis.add("QT间期: " + String.format("%.3f", qtInterval) + "s");
            }
            
            // 检测T波低平 (<0.2mV)
            if (tWaveAmp < T_MIN_AMP && tWaveAmp > 0.05f) {
                stageErrors.add("T波低平: " + String.format("%.3f", tWaveAmp) + "mV (应≥0.2mV)");
            }
            
            // 检测T波倒置
            if (minHeight < -smallGridSize * 0.3f) {
                float invertedAmp = Math.abs(minHeight) / smallGridSize * mvPerSmallGrid;
                stageErrors.add("T波倒置: 深度" + String.format("%.3f", invertedAmp) + "mV");
            }
            
            // 检测T波振幅上限
            if (tWaveAmp > T_MAX_AMP) {
                stageErrors.add("T波振幅超标: " + String.format("%.3f", tWaveAmp) + "mV (应<0.5mV)");
            }
            
            // T/R比值
            if (rWaveAmp > 0) {
                float trRatio = tWaveAmp / rWaveAmp;
                detailedAnalysis.add("T/R比值: " + String.format("%.2f", trRatio));
                
                if (trRatio < T_MIN_RATIO) {
                    stageErrors.add("T波相对不足: T/R=" + String.format("%.2f", trRatio) + " (应>0.1)");
                }
            }
            
            // 检测双峰T波
            if (isBiphasicTWave()) {
                stageErrors.add("T波形态异常: 双峰T波");
            }
        }

        // 检测T波终点 - 回到基线或T波与基线交点
        private PointF detectTWaveEnd() {
            for (int i = currentPathPoints.size() - 1; i >= 0; i--) {
                PointF p = currentPathPoints.get(i);
                if (Math.abs(p.y - baselineY) < smallGridSize * 0.2f) {
                    return p;
                }
            }
            return endPoint;
        }

        // 检测双峰T波
        private boolean isBiphasicTWave() {
            if (currentPathPoints.size() < 10) return false;
            
            int peakCount = 0;
            boolean wasRising = false;
            
            for (int i = 1; i < currentPathPoints.size() - 1; i++) {
                float prev = baselineY - currentPathPoints.get(i-1).y;
                float curr = baselineY - currentPathPoints.get(i).y;
                float next = baselineY - currentPathPoints.get(i+1).y;
                
                if (curr > prev && curr > next && curr > smallGridSize * 0.3f) {
                    peakCount++;
                }
            }
            
            return peakCount >= 2;
        }

        // 线性插值获取某X处的Y值
        private float interpolateYAtX(float x) {
            for (int i = 0; i < currentPathPoints.size() - 1; i++) {
                PointF p1 = currentPathPoints.get(i);
                PointF p2 = currentPathPoints.get(i + 1);
                
                if (x >= p1.x && x <= p2.x) {
                    float ratio = (x - p1.x) / (p2.x - p1.x);
                    return p1.y + ratio * (p2.y - p1.y);
                }
            }
            return baselineY;
        }

        public void showCurrentEvaluation() {
            StringBuilder sb = new StringBuilder();
            
            // 汇总各阶段分析结果
            for (int i = 0; i < stageNames.length; i++) {
                if (stageCompleted[i]) {
                    sb.append("【" + stageNames[i] + "】");
                    boolean hasError = false;
                    for (String err : stageErrors) {
                        if (err.startsWith(stageNames[i].split("")[0])) {
                            sb.append(" ✗\n  " + err + "\n");
                            hasError = true;
                        }
                    }
                    if (!hasError) {
                        sb.append(" ✓ 正常\n");
                    }
                    sb.append("\n");
                }
            }
            
            // 添加详细分析数据
            sb.append("【详细测量数据】\n");
            for (String detail : detailedAnalysis) {
                sb.append(detail + "\n");
            }
            
            post(() -> MainActivity.this.showEvaluationResult(stageErrors.isEmpty(), sb.toString()));
        }

        private void showFinalResult() {
            StringBuilder sb = new StringBuilder();
            
            if (stageErrors.isEmpty()) {
                sb.append("✓ 所有波形参数均在正常范围内！\n\n");
            } else {
                sb.append("发现以下异常：\n\n");
                for (int i = 0; i < stageErrors.size(); i++) {
                    sb.append((i+1) + ". " + stageErrors.get(i) + "\n");
                }
                sb.append("\n");
            }
            
            sb.append("【完整测量报告】\n");
            for (String detail : detailedAnalysis) {
                sb.append(detail + "\n");
            }
            
            post(() -> MainActivity.this.showEvaluationResult(stageErrors.isEmpty(), sb.toString()));
        }

        public void reset() {
            drawnPath.reset();
            currentPathPoints.clear();
            allPathPoints.clear();
            stageErrors.clear();
            detailedAnalysis.clear();
            
            pWaveEnd = null;
            qrsStart = null;
            qrsEnd = null;
            jPoint = null;
            tWaveStart = null;
            tWaveEnd = null;
            
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
            prInterval = 0;
            qrsDuration = 0;
            qtInterval = 0;
            pWaveStarted = false;
            pWaveHasGoneUp = false;
            updateStage(stageNames[0]);
            invalidate();
        }
    }
}
