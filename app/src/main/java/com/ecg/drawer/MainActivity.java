package com.ecg.drawer;

import android.app.AlertDialog;
import android.content.Context;
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
            .setTitle("心电图标准详解")
            .setMessage(getGuideText())
            .setPositiveButton("确定", null)
            .show();
    }

    private void showContactDialog() {
        // 创建滚动视图
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        // 添加文字说明
        TextView textView = new TextView(this);
        textView.setText(getContactText());
        textView.setTextSize(16);
        textView.setLineSpacing(1.5f, 1.5f);
        textView.setPadding(0, 0, 0, 30);
        layout.addView(textView);
        
        // 添加二维码图片
        ImageView qrImage = new ImageView(this);
        qrImage.setImageResource(R.drawable.donation_qr);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            800
        );
        qrImage.setLayoutParams(params);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(qrImage);
        
        scrollView.addView(layout);
        
        new AlertDialog.Builder(this)
            .setTitle("联系我们")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show();
    }

    private String getGuideText() {
        return "【P波标准】\n" +
               "• 时间：＜0.12s（3小格，每格0.04s）\n" +
               "• 幅度：＜0.25mv（2.5小格，每格0.1mv）\n\n" +
               "【PR间期】\n" +
               "• 正常：0.12-0.20s（3-5小格）\n\n" +
               "【QRS波群】\n" +
               "• Q波时间：＜0.04s（1小格）\n" +
               "• Q波幅度：＜1/4 R波\n" +
               "• R波振幅：＞0.5mv（5小格）\n" +
               "• QRS总时间：＜0.12s（3小格）\n\n" +
               "【ST段】\n" +
               "• 压低：＜0.05mv（0.5小格）\n" +
               "• 抬高：弓背上抬为异常！\n" +
               "• 正常形态：水平或轻微下斜\n\n" +
               "【T波】\n" +
               "• I、II、V5-6导联应直立\n" +
               "• 幅度：＞R/10（R波的1/10）\n" +
               "• 最大幅度：＜0.5mv（5小格）\n\n" +
               "━━━━━━━━━━━━━━━━━━━━\n" +
               "© 2025 woP All Rights Reserved";
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
        tvStage.setText("当前阶段：" + stage);
    }

    public void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("不正常")
            .setMessage(message)
            .setPositiveButton("重新绘制", (dialog, which) -> ecgView.reset())
            .setCancelable(false)
            .show();
    }

    class ECGView extends View {
        private Paint gridPaint;
        private Paint ecgPaint;
        private Paint activePaint;
        private Paint textPaint;
        private Paint startPointPaint;
        
        // 坐标纸放大5倍：20f -> 100f
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
        
        // 记录各波形的振幅用于比例计算
        private float pWaveAmp = 0;
        private float qWaveAmp = 0;
        private float rWaveAmp = 0;
        private float sWaveAmp = 0;
        private float tWaveAmp = 0;
        private float maxQRSAmp = 0;
        
        // 标准值
        private final float P_MAX_TIME = 0.12f;
        private final float P_MAX_AMP = 0.25f;
        private final float PR_MIN_TIME = 0.12f;
        private final float PR_MAX_TIME = 0.20f;
        private final float Q_MAX_TIME = 0.04f;
        private final float Q_MAX_RATIO = 0.25f; // Q < 1/4 R
        private final float QRS_MAX_TIME = 0.12f;
        private final float ST_ELEVATION_MAX = 0.1f;
        private final float ST_DEPRESSION_MAX = 0.05f;
        private final float T_MAX_AMP = 0.5f;
        private final float T_MIN_RATIO = 0.1f; // T > R/10
        
        private float secondsPerSmallGrid = 0.04f;
        private float mvPerSmallGrid = 0.1f;

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
            textPaint.setTextSize(30);

            // P波起始点标记画笔
            startPointPaint = new Paint();
            startPointPaint.setColor(Color.GREEN);
            startPointPaint.setStrokeWidth(5);
            startPointPaint.setStyle(Paint.Style.FILL);
            
            drawnPath = new Path();
            currentPathPoints = new ArrayList<>();
            // 基线将在 onSizeChanged 中设置到屏幕中下1/3处
            updateStage(stageNames[0]);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // 设置基线到屏幕中下1/3处
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

        // 绘制P波起始点标志
        private void drawPWaveStartMarker(Canvas canvas) {
            float markerX = smallGridSize; // 最左端留一格空白
            float markerY = baselineY;
            
            // 绘制绿色十字标记
            float markerSize = smallGridSize * 0.8f;
            canvas.drawLine(markerX - markerSize/2, markerY, markerX + markerSize/2, markerY, startPointPaint);
            canvas.drawLine(markerX, markerY - markerSize/2, markerX, markerY + markerSize/2, startPointPaint);
            
            // 绘制文字标注
            Paint markerTextPaint = new Paint();
            markerTextPaint.setColor(Color.GREEN);
            markerTextPaint.setTextSize(35);
            markerTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("P波起点", markerX, markerY + markerSize + 40, markerTextPaint);
        }

        private void drawStageHint(Canvas canvas) {
            String hint = "";
            switch (currentStage) {
                case 0:
                    hint = "【P波】向上圆钝波，时限<3格，高度<2.5格";
                    break;
                case 1:
                    hint = "【PR间期】回到基线，水平线，长度3-5格";
                    break;
                case 2:
                    hint = "【QRS】Q(下)<1格→R(上尖>5格)→S(下)，总宽<3格";
                    break;
                case 3:
                    hint = "【ST段】回到基线，压低<0.5格，警惕弓背上抬！";
                    break;
                case 4:
                    hint = "【T波】向上圆钝波，高度>R/10且<5格";
                    break;
            }
            canvas.drawText(hint, 50, 80, textPaint);
            canvas.drawText("每小格：0.04s / 0.1mv", 50, getHeight() - 50, textPaint);
            
            // 绘制版权水印
            Paint copyrightPaint = new Paint();
            copyrightPaint.setColor(Color.parseColor("#33FF1493")); // 半透明
            copyrightPaint.setTextSize(60);
            copyrightPaint.setTextAlign(Paint.Align.CENTER);
            canvas.save();
            canvas.rotate(-45, getWidth() / 2f, getHeight() / 2f);
            canvas.drawText("woP", getWidth() / 2f, getHeight() / 2f, copyrightPaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentPathPoints.clear();
                    if (currentStage == 0) {
                        drawnPath.reset();
                        // P波从标记位置开始
                        startX = smallGridSize;
                        drawnPath.moveTo(startX, baselineY);
                        startPoint = new PointF(startX, baselineY);
                        lastX = startX;
                        lastY = baselineY;
                    } else {
                        if (Math.abs(x - lastX) > 100) {
                            showError("请从上一阶段的终点继续绘制！");
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
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    endPoint = new PointF(x, y);
                    currentPathPoints.add(new PointF(x, y));
                    if (validateStage()) {
                        lastX = x;
                        lastY = y;
                        currentStage++;
                        if (currentStage >= stageNames.length) {
                            showCompletion();
                        } else {
                            updateStage(stageNames[currentStage]);
                            invalidate();
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private boolean validateStage() {
            float width = Math.abs(endPoint.x - startPoint.x);
            float maxHeight = 0;
            float minHeight = 0;
            
            // 计算路径中的最大和最小高度
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxHeight) maxHeight = h;
                if (h < minHeight) minHeight = h;
            }
            
            float smallGridsX = width / smallGridSize;
            float smallGridsYUp = maxHeight / smallGridSize;
            float smallGridsYDown = Math.abs(minHeight) / smallGridSize;
            
            switch (currentStage) {
                case 0: // P波
                    float pTime = smallGridsX * secondsPerSmallGrid;
                    pWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    
                    if (pTime > P_MAX_TIME) {
                        showError("P波时间超标！\n当前：" + String.format("%.2f", pTime) + "s\n标准：<0.12s");
                        return false;
                    }
                    if (pWaveAmp > P_MAX_AMP) {
                        showError("P波幅度超标！\n当前：" + String.format("%.2f", pWaveAmp) + "mv\n标准：<0.25mv");
                        return false;
                    }
                    if (endPoint.y > baselineY - smallGridSize) {
                        showError("P波应该是向上的！");
                        return false;
                    }
                    return true;
                    
                case 1: // PR间期
                    float prTime = smallGridsX * secondsPerSmallGrid;
                    
                    if (prTime < PR_MIN_TIME || prTime > PR_MAX_TIME) {
                        showError("PR间期不正常！\n当前：" + String.format("%.2f", prTime) + "s\n标准：0.12-0.20s");
                        return false;
                    }
                    if (Math.abs(endPoint.y - baselineY) > smallGridSize * 0.5) {
                        showError("PR间期应在基线上！");
                        return false;
                    }
                    return true;
                    
                case 2: // QRS波群
                    float qrsTime = smallGridsX * secondsPerSmallGrid;
                    
                    // 分析QRS波群结构
                    analyzeQRS();
                    
                    if (qrsTime > QRS_MAX_TIME) {
                        showError("QRS波群时间超标！\n当前：" + String.format("%.2f", qrsTime) + "s\n标准：<0.12s");
                        return false;
                    }
                    
                    // 检测QRS振幅>0.5mv
                    if (rWaveAmp < 0.5f) {
                        showError("QRS振幅不足！\n当前R波：" + String.format("%.2f", rWaveAmp) + "mv\n标准：>0.5mv");
                        return false;
                    }
                    
                    // 检测Q波时间
                    float qTime = detectQTime();
                    if (qTime > Q_MAX_TIME) {
                        showError("Q波时间超标！\n当前：" + String.format("%.2f", qTime) + "s\n标准：<0.04s");
                        return false;
                    }
                    
                    // 检测Q波幅度比例
                    if (qWaveAmp > 0 && rWaveAmp > 0) {
                        float qRratio = qWaveAmp / rWaveAmp;
                        if (qRratio > Q_MAX_RATIO) {
                            showError("Q波幅度超标！\n当前Q/R比值：" + String.format("%.2f", qRratio) + "\n标准：<0.25（即Q<1/4R）");
                            return false;
                        }
                    }
                    
                    // 检查是否有R波
                    if (rWaveAmp <= 0) {
                        showError("QRS波群缺少R波！\n正常QRS应有Q→R→S结构");
                        return false;
                    }
                    
                    return true;
                    
                case 3: // ST段
                    analyzeSTSegment();
                    float stDeviation = smallGridsYUp * mvPerSmallGrid;
                    boolean isElevated = maxHeight > smallGridSize;
                    
                    // 检测弓背上抬（异常形态）
                    if (isElevated && isConvexUpward()) {
                        showError("ST段弓背上抬！\n这是急性心梗的表现，属于异常！\n正常ST段应为水平或轻微下斜");
                        return false;
                    }
                    
                    if (isElevated && stDeviation > ST_ELEVATION_MAX) {
                        showError("ST段抬高超标！\n当前：" + String.format("%.2f", stDeviation) + "mv\n标准：<0.1mv");
                        return false;
                    }
                    
                    float stDepression = smallGridsYDown * mvPerSmallGrid;
                    if (minHeight < -smallGridSize * 0.5 && stDepression > ST_DEPRESSION_MAX) {
                        showError("ST段压低超标！\n当前：" + String.format("%.2f", stDepression) + "mv\n标准：<0.05mv");
                        return false;
                    }
                    return true;
                    
                case 4: // T波
                    analyzeTWave();
                    tWaveAmp = smallGridsYUp * mvPerSmallGrid;
                    
                    if (tWaveAmp > T_MAX_AMP) {
                        showError("T波幅度超标！\n当前：" + String.format("%.2f", tWaveAmp) + "mv\n标准：<0.5mv");
                        return false;
                    }
                    
                    // 检测T波与R波比例
                    if (rWaveAmp > 0) {
                        float trRatio = tWaveAmp / rWaveAmp;
                        if (trRatio < T_MIN_RATIO) {
                            showError("T波幅度不足！\n当前T/R比值：" + String.format("%.2f", trRatio) + "\n标准：>0.1（即T>R/10）");
                            return false;
                        }
                    }
                    
                    if (endPoint.y > baselineY - smallGridSize) {
                        showError("T波应该是直立的！\n在I、II、V5-6导联T波应直立");
                        return false;
                    }
                    return true;
            }
            return true;
        }

        private void analyzeQRS() {
            // 分析QRS波群，计算Q、R、S波的幅度
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            PointF minPoint = null;
            PointF maxPoint = null;
            
            for (PointF p : currentPathPoints) {
                if (p.y < minY) {
                    minY = p.y;
                    minPoint = p;
                }
                if (p.y > maxY) {
                    maxY = p.y;
                    maxPoint = p;
                }
            }
            
            // 计算各波幅度
            float baseline = baselineY;
            
            // R波是最高点（向上）
            if (maxPoint != null && maxPoint.y < baseline) {
                rWaveAmp = (baseline - maxPoint.y) / smallGridSize * mvPerSmallGrid;
            }
            
            // Q波和S波是最低点（向下）
            if (minPoint != null && minPoint.y > baseline) {
                float downAmp = (minPoint.y - baseline) / smallGridSize * mvPerSmallGrid;
                // 根据位置判断是Q波还是S波
                if (minPoint.x < startPoint.x + (endPoint.x - startPoint.x) * 0.3) {
                    qWaveAmp = downAmp;
                } else {
                    sWaveAmp = downAmp;
                }
            }
            
            maxQRSAmp = Math.max(rWaveAmp, Math.max(qWaveAmp, sWaveAmp));
        }

        private float detectQTime() {
            // 检测Q波时间（向下的部分持续时间）
            float qStart = 0, qEnd = 0;
            boolean inQ = false;
            
            for (int i = 0; i < currentPathPoints.size(); i++) {
                PointF p = currentPathPoints.get(i);
                if (p.y > baselineY + smallGridSize * 0.3) {
                    if (!inQ) {
                        inQ = true;
                        qStart = p.x;
                    }
                    qEnd = p.x;
                } else if (inQ && p.y < baselineY) {
                    break;
                }
            }
            
            return (qEnd - qStart) / smallGridSize * secondsPerSmallGrid;
        }

        private void analyzeSTSegment() {
            // ST段分析已在validateStage中完成
        }

        private void analyzeTWave() {
            // T波分析
            float maxT = 0;
            for (PointF p : currentPathPoints) {
                float h = baselineY - p.y;
                if (h > maxT) maxT = h;
            }
            tWaveAmp = maxT / smallGridSize * mvPerSmallGrid;
        }

        private boolean isConvexUpward() {
            // 检测是否为弓背上抬（凸向上形态）
            // 通过计算中点高度来判断
            if (currentPathPoints.size() < 3) return false;
            
            int midIndex = currentPathPoints.size() / 2;
            PointF midPoint = currentPathPoints.get(midIndex);
            PointF start = currentPathPoints.get(0);
            PointF end = currentPathPoints.get(currentPathPoints.size() - 1);
            
            // 计算连接起点和终点的直线在中点处的Y值
            float lineYAtMid = start.y + (end.y - start.y) * 0.5f;
            
            // 如果实际中点明显低于直线（屏幕坐标Y向下增加），则为弓背向上
            return midPoint.y > lineYAtMid + smallGridSize * 0.5;
        }

        private void showCompletion() {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("恭喜！")
                .setMessage("您已成功绘制一个标准的心电图波形！\n\n所有参数均在正常范围内：\n" +
                           "• P波时限<0.12s，幅度<0.25mv\n" +
                           "• PR间期0.12-0.20s\n" +
                           "• Q波<1/4R，QRS<0.12s\n" +
                           "• ST段无弓背上抬\n" +
                           "• T波>R/10且<0.5mv")
                .setPositiveButton("再画一次", (dialog, which) -> reset())
                .setCancelable(false)
                .show();
        }

        public void reset() {
            drawnPath.reset();
            currentPathPoints.clear();
            currentStage = 0;
            lastX = 0;
            lastY = 0;
            pWaveAmp = 0;
            qWaveAmp = 0;
            rWaveAmp = 0;
            sWaveAmp = 0;
            tWaveAmp = 0;
            maxQRSAmp = 0;
            updateStage(stageNames[0]);
            updateStatus("准备就绪");
            invalidate();
        }
    }
}
