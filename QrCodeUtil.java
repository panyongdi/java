package com.wenwenmao.proj.wncode.utils;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.wenwenmao.core.exception.ApiProcessException;
import com.wenwenmao.module.sys.entity.MSysUser;
import com.wenwenmao.proj.wncode.config.WncodeConfig;
import com.wenwenmao.proj.wncode.constants.ErrorCode;
import com.wenwenmao.proj.wncode.constants.enums.CodeType;
import com.wenwenmao.proj.wncode.constants.enums.JumpType;
import com.wenwenmao.proj.wncode.entity.PWncodeQrcode;
import com.wenwenmao.proj.wncode.entity.PWncodeQrcodeBatch;
import com.wenwenmao.proj.wncode.mongdb.entity.PWncodeSubCode;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sun.misc.BASE64Encoder;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 *
 */
@Component
public class QrCodeUtil {
    private static final String CHARSET = "utf-8";
    private static final String FORMAT_NAME = "PNG";
    // 二维码尺寸
    private static  int QRCODE_SIZE = 500;
    // LOGO宽度
    private static final int WIDTH = 60;
    // LOGO高度
    private static final int HEIGHT = 60;

    @Autowired
    WncodeConfig wncodeConfig;

    public static WncodeConfig config;

    @PostConstruct
    public void init() {
        config= wncodeConfig;
    }

    private static BufferedImage createImage(String content, String imgPath,String sColor,String eColor, boolean needCompress,Integer size) throws Exception {
        Hashtable hints = new Hashtable();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
        hints.put(EncodeHintType.MARGIN, 0);
        if (size!=null){
            QRCODE_SIZE=size;
        }else {
            QRCODE_SIZE=QRCODE_SIZE;
        }
        BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QRCODE_SIZE, QRCODE_SIZE,
                hints);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        if (sColor!=null&&sColor.startsWith("#")){
            sColor=sColor.substring(1,sColor.length());
        }else {
            sColor="000000";
        }
        if (eColor!=null&&eColor.startsWith("#")){
            eColor=eColor.substring(1,eColor.length());
        }else {
            eColor="000000";
        }
        for (int x = 0; x < width; x++) {

            int startNum=Integer.parseInt(sColor,16) ;
            int endNum=Integer.parseInt(eColor,16) ;
            ArrayList<Integer> colorList=null;
            colorList=gradient(startNum,endNum,height*2);

            for (int y = 0; y < height; y++) {
                if (colorList.size()<=y){
                    image.setRGB(x, y, bitMatrix.get(x, y) ? endNum : 0xFFFFFFFF);
                }else {
                    image.setRGB(x, y, bitMatrix.get(x, y) ? colorList.get(y) : 0xFFFFFFFF);
                }


            }
        }
        if (imgPath == null || "".equals(imgPath)) {
            return image;
        }
        // 插入图片
        QrCodeUtil.insertImage(image, imgPath, needCompress,QRCODE_SIZE/4);
        return image;
    }

    /**
     * 指定长度的渐变。
     * @param c0 起始颜色。
     * @param c1 结束颜色。
     * @param len 受限的渐变长度，为保证每一个颜色都不一样，会根据颜色找出长度最大值。
     * @return 长度为参数 len+1 的所有颜色。
     */
    public static ArrayList<Integer> gradient(int c0, int c1, double len) {
        int[] fc = { (c0 & 0xff0000) >> 16, (c0 & 0xff00) >> 8, c0 & 0xff };
        int[] tc = { (c1 & 0xff0000) >> 16, (c1 & 0xff00) >> 8, c1 & 0xff };
        len = Math.min(len, Math.max(Math.max(Math.abs(fc[0] - tc[0]), Math.abs(fc[1] - tc[1])), Math.abs(fc[2] - tc[2])));
        double[] s = { (tc[0] - fc[0]) / len, (tc[1] - fc[1]) / len, (tc[2] - fc[2]) / len, };
        ArrayList<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < len; i++) {
            r.add(fc[0] + (int) (i * s[0]) << 16 | fc[1] + (int) (i * s[1]) << 8 | fc[2] + (int) (i * s[2]));
            r.add(fc[0] + (int) (i * s[0]) << 16 | fc[1] + (int) (i * s[1]) << 8 | fc[2] + (int) (i * s[2]));

        }
        r.add(c1);
        return r;
    }



    private static void insertImage(BufferedImage source, String imgPath, boolean needCompress,Integer size) throws Exception {
//        File file = new File(imgPath);
//        if (!file.exists()) {
//            System.err.println("" + imgPath + "   该文件不存在！");
//            return;
//        }
        InputStream inputStream=getInputStreamFromUrl(imgPath);
        Image src =null;
        try{
            src = ImageIO.read(inputStream);
        }catch (Exception e){
            // Get the reader
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No reader for: " + inputStream);
            }
            ImageReader reader = readers.next();
            try {
                ImageReadParam param = reader.getDefaultReadParam();
                // Finally read the image, using settings from param
                src = reader.read(0, param);

            }
            finally {
                // Dispose reader in finally block to avoid memory leaks
                reader.dispose();
            }
        }
        int width = src.getWidth(null);
        int height = src.getHeight(null);
        if (size==null){
            size=QRCODE_SIZE;
        }
        width=size;
        height=size;
        if (needCompress) { // 压缩LOGO
            if (width > WIDTH) {
                width = WIDTH;
            }
            if (height > HEIGHT) {
                height = HEIGHT;
            }
            Image image = src.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage tag = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics g = tag.getGraphics();
            g.drawImage(image, 0, 0, null); // 绘制缩小后的图
            g.dispose();
            src = image;
        }
        // 插入LOGO
        Graphics2D graph = source.createGraphics();
        int x = (QRCODE_SIZE - width) / 2;
        int y = (QRCODE_SIZE - height) / 2;
        graph.drawImage(src, x, y, width, height, null);
        Shape shape = new RoundRectangle2D.Float(x, y, width, width, 6, 6);
        graph.setStroke(new BasicStroke(3f));
        graph.draw(shape);
        graph.dispose();
    }

    /**
     * 通过网络地址获取文件InputStream
     *
     * @param path 地址
     * @return
     */
    public static InputStream getInputStreamFromUrl(String path) {
        URL url = null;
        InputStream is = null;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent","Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            conn.setDoInput(true);
            conn.connect();
            is = conn.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return is;
    }

    /**
     * 创建二维码
     * @param content 二维码内容
     * @param imgPath 二维码logo链接
     * @param sColor 开始颜色
     * @param eColor 结束颜色
     * @param needCompress 是否压缩logo
     * @param size 大小
     * @return base64位数据
     * @throws Exception
     */
    public static String encodeBase64(String content, String imgPath,String sColor,String eColor, boolean needCompress,Integer size) throws Exception {
        BufferedImage image = QrCodeUtil.createImage(content, imgPath,sColor,eColor, needCompress,size);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();//io流
        ImageIO.write(image, "png", baos);//写入流中
        byte[] bytes = baos.toByteArray();//转换成字节
        BASE64Encoder encoder = new BASE64Encoder();
        String png_base64 = encoder.encodeBuffer(bytes).trim();//转换成base64串
        png_base64 = png_base64.replaceAll("\n", "").replaceAll("\r", "");//删除 \r\n
//        System.out.println("值为："+"data:image/jpg;base64,"+png_base64);
        png_base64="data:image/jpg;base64,"+png_base64;
        return png_base64;
    }

    /**
     * 创建二维码
     * @param content 二维码内容
     * @param imgPath 二维码logo链接
     * @param fileName 文件名
     * @param sColor 开始颜色
     * @param eColor 结束颜色
     * @param fileType 文件类型
     * @param folder 文件夹
     * @param needCompress 是否压缩logo
     * @param size 大小
     * @return oss链接
     * @throws Exception
     */
    public static String encodeOssUrl(String content, String imgPath, String fileName,String fileType,String sColor,String eColor, boolean needCompress,String folder,Integer size) throws Exception {
        BufferedImage image = QrCodeUtil.createImage(content, imgPath,sColor,eColor, needCompress,size);
        fileName=fileName+"."+fileType;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();//io流
        ImageIO.write(image, "png", baos);//写入流中

        String imgUrl=OssUtil.uploadQrcode(new ByteArrayInputStream(baos.toByteArray()),fileName,folder);
        System.out.println("imgUrl:"+imgUrl);
        return imgUrl;
    }

    /**
     * 创建二维码
     * @param content 二维码内容
     * @param imgPath 二维码logo链接
     * @param fileName 文件名
     * @param sColor 开始颜色
     * @param eColor 结束颜色
     * @param fileType 文件类型
     * @param needCompress 是否压缩logo
     * @param size 大小
     * @return oss链接
     * @throws Exception
     */
    public static ByteArrayOutputStream encodeByte(String content, String imgPath, String fileName,String fileType,String sColor,String eColor, boolean needCompress,Integer size) throws Exception {
        BufferedImage image = QrCodeUtil.createImage(content, imgPath,sColor,eColor, needCompress,size);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();//io流
        ImageIO.write(image, "png", baos);//写入流中

        return baos;
    }
    public static BufferedImage encode(String content, String imgPath, boolean needCompress,Integer size) throws Exception {
        BufferedImage image = QrCodeUtil.createImage(content, imgPath,null,null, needCompress,size);
        return image;
    }

    public static void mkdirs(String destPath) {
        File file = new File(destPath);
        // 当文件夹不存在时，mkdirs会自动创建多层目录，区别于mkdir．(mkdir如果父目录不存在则会抛出异常)
        if (!file.exists() && !file.isDirectory()) {
            file.mkdirs();
        }
    }


    public static void encode(String content, String imgPath, OutputStream output, String sColor,String eColor,boolean needCompress,Integer size)
            throws Exception {
        BufferedImage image = QrCodeUtil.createImage(content, imgPath,sColor,eColor, needCompress,size);
        ImageIO.write(image, FORMAT_NAME, output);
    }


    public static String decode(File file) throws Exception {
        BufferedImage image;
        image = ImageIO.read(file);
        if (image == null) {
            return null;
        }
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result;
        Hashtable hints = new Hashtable();
        hints.put(DecodeHintType.CHARACTER_SET, CHARSET);
        result = new MultiFormatReader().decode(bitmap, hints);
        String resultStr = result.getText();
        return resultStr;
    }

    public static String decode(String path) throws Exception {
        return QrCodeUtil.decode(new File(path));
    }


    /**
     * 根据str,font的样式以及输出文件目录
     * @param str	字符串
     * @param font	字体
     * @param fileName	文件名称
     * @param width	宽度
     * @param height	高度
     * @throws Exception
     */
    public static Map<String,Object> createImageLogo(String str, Font font, String destPath,String fileName,String fileType,
                                   Integer width, Integer height) throws Exception {
        // 创建图片
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_BGR);
        Graphics g = image.getGraphics();
        g.setClip(0, 0, width, height);
        g.setColor(Color.GRAY);
        // 先用黑色填充整张图片,也就是背景
        g.fillRect(0, 0, width, height);
        // 在换成红色
        g.setColor(Color.black);
        // 设置画笔字体
        g.setFont(font);
        /** 用于获得垂直居中y */
        Rectangle clip = g.getClipBounds();
        FontMetrics fm = g.getFontMetrics(font);
        int ascent = fm.getAscent();
        int descent = fm.getDescent();
        int y = (clip.height - (ascent + descent)) / 2 + ascent;

        // 256 340 0 680
        g.drawString(str, (width-fm.stringWidth(str))/2, y);

        g.dispose();


        fileName=fileName+"."+fileType;
        if (destPath!=null){
            destPath=destPath+"/"+fileName;
            mkdirs(destPath);
            ImageIO.write(image, FORMAT_NAME, new File(destPath));
        }

        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        ImageIO.write(image,"png",baos);
        Map<String,Object> returnMap=new HashMap<>();
        returnMap.put("baos",baos);
        returnMap.put("fileName",fileName);
        return returnMap;
    }

    /**
     *  下载子码
     * @param response
     * @param subCodeList 子码码批次顺序
     * @throws Exception
     */
    public static void downloadSubCode(HttpServletResponse response, List<PWncodeSubCode> subCodeList) throws Exception {
        String outputFileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        // 设置response参数
        response.reset();
        response.setContentType("content-type:octet-stream;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + new String((outputFileName).getBytes(), "iso-8859-1"));
        ServletOutputStream out = response.getOutputStream();

        ZipArchiveOutputStream zous = new ZipArchiveOutputStream(out);
        zous.setUseZip64(Zip64Mode.AsNeeded);
        for (int i = 0; i <subCodeList.size(); i++) {
            PWncodeSubCode subCode=subCodeList.get(i);
            if (subCode.getQrcodeUrl()!=null){
                //包含跳转链接的二维码
                InputStream inputStream=getInputStreamFromUrl(subCode.getQrcodeUrl());
                ByteArrayOutputStream qrCodeBaos= parse(inputStream);
                addImgToRar(zous,qrCodeBaos,subCode.getSubCode());
            }else {
                BufferedImage image = QrCodeUtil.createImage(CodeType.SUB_CODE.getCode()+"&"+subCode.getSubCode(), null,null,null, true,null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();//io流
                ImageIO.write(image, "png", baos);//写入流中
                addImgToRar(zous,baos,subCode.getSubCode());
            }
        }
        if(zous!=null) {
            zous.close();
        }
    }
    /**
     *
     * @param request
     * @param response
     * @param qrcodeBatchList 图文码批次顺序
     * @throws Exception
     */
    public static void download2(HttpServletRequest request, HttpServletResponse response, List<PWncodeQrcodeBatch> qrcodeBatchList) throws Exception {
        String outputFileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        // 设置response参数
        response.reset();
        response.setContentType("content-type:octet-stream;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + new String((outputFileName).getBytes(), "iso-8859-1"));
        ServletOutputStream out = response.getOutputStream();

        ZipArchiveOutputStream zous = new ZipArchiveOutputStream(out);
        zous.setUseZip64(Zip64Mode.AsNeeded);

        for (int i = 0; i <qrcodeBatchList.size(); i++) {
            PWncodeQrcodeBatch qrcodeBatch=qrcodeBatchList.get(i);
            //包含跳转链接的二维码
            InputStream inputStream=getInputStreamFromUrl(qrcodeBatch.getHttpCode());
            ByteArrayOutputStream qrCodeBaos= parse(inputStream);
            addImgToRar(zous,qrCodeBaos,qrcodeBatch.getBatchNo());

            //只包含批次号的二维码
            InputStream numInputStream=getInputStreamFromUrl(qrcodeBatch.getNumCode());
            ByteArrayOutputStream numBaos= parse(numInputStream);
            addImgToRar(zous,numBaos,qrcodeBatch.getBatchNo()+"code");
        }

        if(zous!=null) {
            zous.close();
        }
    }

    public static ByteArrayOutputStream parse(InputStream in) throws Exception
    {
        ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            swapStream.write(ch);
        }
        return swapStream;
    }
    public static void addImgToRar(ZipArchiveOutputStream zous, ByteArrayOutputStream baos, String fileName) throws IOException {
        fileName=fileName+".png";
        if (baos != null) {
            baos.flush();
        }
        byte[] bytes = baos.toByteArray();
        //设置文件名
        ArchiveEntry entry = new ZipArchiveEntry(fileName);
        zous.putArchiveEntry(entry);
        zous.write(bytes);
        zous.closeArchiveEntry();
        if (baos != null) {
            baos.close();
        }
    }

    /**
     * 生成跳转二维码
     * @param content 子码信息
     * @param logoUrl logo地址
     * @param userId 用户信息
     * @param codeType 母码类型
     * @param isHttpUrl 是否是带有跳转链接的二维码
     * @return
     * @throws ApiProcessException
     */
    public static String updateHttpQrCode(String content, String userId, CodeType codeType,Boolean isHttpUrl,String logoUrl,String fileName,String sColor,String endColor,Integer size) throws ApiProcessException {
        StringBuffer httpUrl=new StringBuffer();
        httpUrl.append(config.getHttpUrlStart());
        httpUrl.append("?type=").append(codeType.getCode())
                .append("&code=").append(content)
                .append("&state=").append(JumpType.FRONT_TYPE.getCode());
        if (isHttpUrl){
            content=httpUrl.toString();
        }
        try {
            String qrcodeUrl= QrCodeUtil.encodeOssUrl(content,logoUrl,fileName,"png",sColor,endColor,true,userId,size);
            return qrcodeUrl;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ApiProcessException(ErrorCode.CREATE_QRCODE_TYPE_ERROR);
        }
    }
}
