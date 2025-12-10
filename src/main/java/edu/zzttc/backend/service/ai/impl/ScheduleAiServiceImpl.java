package edu.zzttc.backend.service.ai.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import edu.zzttc.backend.domain.entity.TermConfig;
import edu.zzttc.backend.service.ai.ScheduleAiService;
import edu.zzttc.backend.service.schedule.model.ScheduleParsed;
import edu.zzttc.backend.service.schedule.model.ScheduleParsedCourse;
import edu.zzttc.backend.service.schedule.model.ScheduleParsedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ScheduleAiServiceImpl implements ScheduleAiService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String dashscopeBaseUrl;

    @Value("${spring.ai.dashscope.chat.options.model:qwen3-vl-plus}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final java.util.Set<String> COMMON_SURNAME_CHARS = new java.util.HashSet<>(
            java.util.Arrays.asList("赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈", "韩", "杨",
                    "朱", "秦", "尤", "许", "何", "吕", "施", "张", "孔", "曹", "严", "华", "金", "魏", "陶", "姜", "戚", "谢", "邹",
                    "喻", "柏", "水", "窦", "章", "云", "苏", "潘", "葛", "奚", "范", "彭", "郎", "鲁", "韦", "昌", "马", "苗", "凤",
                    "花", "方", "俞", "任", "袁", "柳", "鲍", "史", "唐", "费", "廉", "岑", "薛", "雷", "贺", "倪", "汤", "罗", "毕",
                    "郝", "邬", "安", "常", "乐", "于", "时", "傅", "皮", "卞", "齐", "康", "伍", "余", "元", "顾", "孟", "平", "黄",
                    "和", "穆", "萧", "尹", "姚", "邵", "湛", "汪", "祁", "毛", "禹", "狄", "米", "贝", "明", "臧", "计", "伏", "成",
                    "戴", "谈", "宋", "茅", "庞", "熊", "纪", "舒", "屈", "项", "祝", "董", "梁", "杜", "阮", "蓝", "闵", "席", "季",
                    "麻", "强", "贾", "路", "娄", "危", "支", "柯", "管", "卢", "莫", "经", "房", "裘", "干", "解", "应", "宗", "丁",
                    "宣", "邓", "郁", "单", "杭", "洪", "包", "诸", "左", "石", "崔", "吉", "钮", "龚", "程", "嵇", "邢", "滑", "裴",
                    "陆", "荣", "辛", "阎", "赫", "皮", "鲜", "敖", "詹", "仇", "冉", "宓", "隗", "瞿", "阚", "胥", "佘", "阴"));

    private static final java.util.List<String> COURSE_SUFFIXES = java.util.Arrays
            .asList("学", "论", "术", "技", "设计", "课程", "基础", "原理", "概论", "思想", "政策", "英语", "体育", "实验", "工程", "管理",
                    "数据", "网络", "计算", "技术");

    @Override
    public ScheduleParsed parseScheduleFromImage(String imagePath, TermConfig termConfig) {
        try {
            Path path = Path.of(imagePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("课表图片不存在: " + imagePath);
            }

            // 1. 图片转 data-url
            String dataUrl = toDataUrl(path);

            // 2. 让 Qwen 把整张课表读成「规范课表文本」
            String normalizedText = callOcrToNormalizedSchedule(dataUrl);
            log.info("Qwen 规范课表文本：{}", normalizedText);

            if (normalizedText == null || normalizedText.trim().length() < 30) {
                throw new RuntimeException("OCR 结果过短或为空");
            }

            // 3. 本地解析这段规范文本 -> ScheduleParsed
            ScheduleParsed parsed = parseNormalizedSchedule(normalizedText.trim());

            if (parsed.getCourses() == null || parsed.getCourses().isEmpty()
                    || parsed.getItems() == null || parsed.getItems().isEmpty()) {
                throw new RuntimeException("从规范文本中解析不到课程信息");
            }

            parsed.setRawOcrText(normalizedText);
            parsed.setImageUrl(imagePath);

            return parsed;
        } catch (Exception e) {
            log.error("调用 Qwen 解析课表失败", e);
            throw new RuntimeException("课表解析失败，请稍后重试");
        }
    }

    // ===================== 1. 新 OCR：直接输出规范课表文本 =====================

    /**
     * 让 Qwen 把整张课表读成「规范课表文本」，大概格式如下：
     *
     * 星期一：第一、二节 网络安全攻防技术 赵洋 R0902840.01 1-7周 第二教学楼104；
     * 第一、二节 网络安全攻防技术 罗绪成 R0902840.01 9-13周 第二教学楼104；
     * 第一、二节 网络安全攻防技术 罗绪成 R0902840.01 14-15周 信软楼西305、信软楼西306；
     * 第三、四节 专业写作基础 张培培 A6200810.02 1-9周 第二教学楼408；
     * 第五、六、七、八节 企业合作课程 T0902820.01 1-9周 科技实验大楼705；
     * 第九、十节 网络安全攻防技术 赵洋 R0902840.01 7周 信软楼西305、信软楼西306；
     * 第九、十节 进阶式挑战性综合项目Ⅲ（互联网安全） 聂旭云 R0906530.03 1-3周 信软楼西305、信软楼西306；
     * ...
     * 星期二：第一、二节 马克思主义基本原理 郭英蕊 M1801230.06 7周 第二教学楼212；
     * 第三、四节 习近平新时代中国特色社会主义思想概论 梁宇 M1801330.05 1-11周 第二教学楼204；
     * ...
     */
    private String callOcrToNormalizedSchedule(String dataUrl) {
        String prompt = """
                你将看到一张大学“学生课表”的截图，请你根据图片内容，输出一段【规范的课表文本】。

                非常重要：你不要逐行原样 OCR，也不要输出 JSON，
                而是要把整张课表转写成下面这种结构清晰、便于程序解析的长句式。

                目标格式示例（务必仿照这一格式组织输出）：

                星期一： 第一、二节 网络安全攻防技术 赵洋 R0902840.01 1-7周 第二教学楼104；
                        第一、二节 网络安全攻防技术 罗绪成 R0902840.01 9-13周 第二教学楼104；
                        第一、二节 网络安全攻防技术 罗绪成 R0902840.01 14-15周 信软楼西305、信软楼西306；
                        第三、四节 专业写作基础 张培培 A6200810.02 1-9周 第二教学楼408；
                        第五、六、七、八节 企业合作课程 T0902820.01 1-9周 科技实验大楼705；
                        第九、十节 网络安全攻防技术 赵洋 R0902840.01 7周 信软楼西305、信软楼西306；
                        第九、十节 进阶式挑战性综合项目Ⅲ（互联网安全） 聂旭云 R0906530.03 1-3周 信软楼西305、信软楼西306；
                        第九、十节 进阶式挑战性综合项目Ⅲ（互联网安全） 聂旭云 R0906530.03 5-6周 信软楼西305、信软楼西306；
                        第九、十节 进阶式挑战性综合项目Ⅲ（互联网安全） 聂旭云 R0906530.03 8-14周 信软楼西305、信软楼西306；
                星期二：第一、二节 马克思主义基本原理 郭英蕊 M1801230.06 7周 第二教学楼212；
                        第三、四节 习近平新时代中国特色社会主义思想概论 梁宇 M1801330.05 1-11周 第二教学楼204；
                星期三：第一、二节 毛泽东思想和中国特色社会主义理论体系概论 黎吉秀 M1801430.72 1-12周 第二教学楼105；
                        第三、四节 马克思主义基本原理 郭英蕊 M1801230.06 1-12周 第二教学楼106；
                        第五、六节 网络安全攻防技术 赵洋 R0902840.01 1-7周 第二教学楼104；
                        第五、六节 网络安全攻防技术 罗绪成 R0902840.01 9-15周 第二教学楼104；
                        第九、十节 进阶式挑战性综合项目Ⅲ（互联网安全） 聂旭云 R0906530.03 1-14周 信软楼西305、信软楼西306；
                星期四：第三、四节 习近平新时代中国特色社会主义思想概论 梁宇 M1801330.05 1-11周 第二教学楼204；
                        第五、六节 网络安全攻防技术 赵洋 R0902840.01 1-7周 第二教学楼104；
                星期五：第一、二节 马克思主义基本原理 郭英蕊 M1801230.06 1-6周 第二教学楼106；
                        第一、二节 马克思主义基本原理 郭英蕊 M1801230.06 8-11周 第二教学楼106；
                        第三、四节 毛泽东思想和中国特色社会主义理论体系概论 黎吉秀 M1801430.72 1-11周 第二教学楼105；
                星期六：第五、六节 形势与政策 商继政 M1800220.C4 14周 第二教学楼206；

                要求：

                1. 结构固定：
                   - 一定按“星期一：...； ...；”这样的顺序输出，
                     再依次是“星期二：...；”，“星期三：...；”……，直到有课的最后一天；
                   - 每天内部，每一门课程之间用中文分号“；”分隔；
                   - 每门课的一行字段顺序固定为：
                     「第X、Y节 或 第X、Y、Z、W节」 + 空格 +
                     「课程名称」 + 空格 +
                     「任课教师（如有）」 + 空格 +
                     「课程代码」 + 空格 +
                     「周次（如 1-7周/7周/14周）」 + 空格 +
                     「教室（如有多个教室，用中文顿号“、”连接）」。

                2. 周次用阿拉伯数字，如：1-7周、7周、14周、1-14周 等。

                3. 课程节次用中文数字，如：第一、二节；第三、四节；第五、六、七、八节；第九、十节；第十一、十二节。

                4. 对于连续占用超过两节的课程，必须完整列出所有节次，不得缩写或遗漏。例如：连续四节必须输出“第七、八、九、十节”。

                5. 若同一课程在不同的周次使用不同的教室/教师/课程代码，必须拆成多行，每行仅对应一组周次及其教室，不要合并在括号或逗号中。例如：
                   星期一：第三、四节 会计学 鲜文铎 R0905730.01 13周 信软楼西305；
                           第三、四节 会计学 鲜文铎 R0905730.01 10-12周 第二教学楼204；

                6. 当同一课程方块中以括号或逗号列出多组“周次+教室”配对（如：“（13,信软楼西305）（连1-12,第二教学楼204）”），这些配对都对应该方块的同一节次，必须逐组展开为多行：
                   例如：第三、四节 会计学 鲜文铎 R0905730.01 （13,信软楼西305）（连1-12,第二教学楼204）
                   输出为：
                   第三、四节 会计学 鲜文铎 R0905730.01 13周 信软楼西305；
                   第三、四节 会计学 鲜文铎 R0905730.01 1-12周 第二教学楼204；
                   注意：不要把“13周”误归到其它节次，也不要合并为一行。

                7. 不同节次的周次不要合并；即便教室相同，也要按各自的节次分行输出（例如同一天同时存在“第一、二节 … 13周 …”和“第三、四节 … 13周 …”，必须分别输出两行）。

                8. 对截图中出现的“连10-12”“10～12”“10至12”“10—12”等表达，统一规范为“10-12周”。务必保证每一条都含有“周次”字段。

                9. 输出时不要换行太频繁，可以每一天独立一行或者换行分段，但不能打乱上述字段顺序和分隔符。

                10. 只输出这一整段规范课表文本，不要输出任何解释、说明、Markdown、JSON 或其它内容。

                11. 当同一门课程在同一天、同一教室由不同教师分别授课（且周次不同）时：
                    必须为每位教师输出“完全一致的节次范围”，不要把节次拆分到不同教师名下；
                    节次范围以课表方块的实际起止为准。
                    示例（正确）：
                    第九、十、十一节 交通规划原理 代壮 R0903830.01 7-12周 第二教学楼208；
                    第九、十、十一节 交通规划原理 熊耀华 R0903830.01 1-6周 第二教学楼208；
                """;

        JSONObject imageObj = new JSONObject();
        imageObj.put("type", "image_url");
        JSONObject urlObj = new JSONObject();
        urlObj.put("url", dataUrl);
        imageObj.put("image_url", urlObj);

        JSONObject textObj = new JSONObject();
        textObj.put("type", "text");
        textObj.put("text", prompt);

        JSONArray contentArr = new JSONArray();
        contentArr.add(imageObj);
        contentArr.add(textObj);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", contentArr);

        JSONArray messages = new JSONArray();
        messages.add(userMsg);

        String resp = callDashScope(messages);
        return resp == null ? "" : resp.trim();
    }

    // ===================== 2. 本地解析规范文本 -> ScheduleParsed =====================

    private ScheduleParsed parseNormalizedSchedule(String text) {
        // 去掉多余换行，统一成一行/少量空格
        String normalized = text.replaceAll("[\\r\\n]+", " ");
        // 方便匹配：去掉多余空格
        normalized = normalized.replaceAll("\\s+", " ").trim();

        Map<String, Integer> dayMap = new HashMap<>();
        dayMap.put("一", 1);
        dayMap.put("二", 2);
        dayMap.put("三", 3);
        dayMap.put("四", 4);
        dayMap.put("五", 5);
        dayMap.put("六", 6);
        dayMap.put("日", 7);
        dayMap.put("天", 7);

        // 匹配 “星期一：” 这样的片段
        Pattern dayPattern = Pattern.compile("星期([一二三四五六日天])\\s*[:：]");
        Matcher m = dayPattern.matcher(normalized);

        List<DayBlock> blocks = new ArrayList<>();
        while (m.find()) {
            String dayChar = m.group(1);
            Integer dayOfWeek = dayMap.get(dayChar);
            if (dayOfWeek == null) {
                continue;
            }
            int start = m.end(); // 内容从冒号后开始
            blocks.add(new DayBlock(dayOfWeek, start));
        }
        if (blocks.isEmpty()) {
            throw new RuntimeException("规范文本中没有识别到“星期X：”结构");
        }

        // 为每个 block 设置结束位置
        for (int i = 0; i < blocks.size(); i++) {
            DayBlock b = blocks.get(i);
            int end = (i + 1 < blocks.size()) ? blocks.get(i + 1).startIndex : normalized.length();
            b.endIndex = end;
        }

        List<ScheduleParsedItem> items = new ArrayList<>();
        Map<String, ScheduleParsedCourse> courseMap = new LinkedHashMap<>();

        for (DayBlock b : blocks) {
            String dayContent = normalized.substring(b.startIndex, b.endIndex).trim();
            if (dayContent.isEmpty())
                continue;
            parseDayContent(b.dayOfWeek, dayContent, items, courseMap);
        }

        ScheduleParsed parsed = new ScheduleParsed();
        parsed.setItems(items);
        parsed.setCourses(new ArrayList<>(courseMap.values()));
        return parsed;
    }

    private void parseDayContent(int dayOfWeek,
            String dayContent,
            List<ScheduleParsedItem> items,
            Map<String, ScheduleParsedCourse> courseMap) {
        // 按分号拆成一门一门课
        String[] courseSegs = dayContent.split("[；;]");
        for (String seg : courseSegs) {
            String s = seg.trim();
            if (s.isEmpty())
                continue;
            if (s.matches("^星期[一二三四五六日天]\\s*[:：]?$"))
                continue;
            java.util.List<ScheduleParsedItem> segItems = parseSingleCourseSeg(dayOfWeek, s, courseMap);
            if (segItems != null && !segItems.isEmpty()) {
                items.addAll(segItems);
            }
        }
    }

    private java.util.List<ScheduleParsedItem> parseSingleCourseSeg(int dayOfWeek,
            String seg,
            Map<String, ScheduleParsedCourse> courseMap) {
        // 先拆出 “第X、Y节 / 第X、Y、Z、W节”
        Pattern p = Pattern.compile("^第([^节]+)节\\s+(.+)$");
        Matcher m = p.matcher(seg);
        if (!m.find()) {
            log.warn("无法从片段中解析节次信息: {}", seg);
            return java.util.Collections.emptyList();
        }
        String sectionText = m.group(1).trim(); // 如 “一、二” / “五、六、七、八”
        String rest = m.group(2).trim();

        // 1. 节次解析
        int[] sectionInfo = parseSectionText(sectionText);
        int sectionStart = sectionInfo[0];
        int sectionCount = sectionInfo[1];

        // 2. 周次 & 课程代码 & 课程名 / 老师 / 教室
        // 2.1 先找课程代码（大写字母开头）
        String courseCode = null;
        Pattern codePat = Pattern.compile("(?<![A-Za-z0-9])([A-Z][0-9A-Z.\\-]*\\d[0-9A-Z.\\-]*)(?![A-Za-z0-9])");
        Matcher codeM = codePat.matcher(rest);
        if (codeM.find()) {
            courseCode = codeM.group(1);
            rest = (rest.substring(0, codeM.start()) + rest.substring(codeM.end())).trim();
        }

        // 2.2 找周次：如 1-7周、7周、14周、1-14周
        String weekExpr = null;
        Pattern weekPat = Pattern.compile("(\\d+(?:-\\d+)?)周");
        Integer weekStart = null;
        Integer weekEnd = null;
        java.util.List<int[]> weekRanges = new java.util.ArrayList<>();
        String restWithWeeks = rest;
        Matcher weekM = weekPat.matcher(restWithWeeks);
        while (weekM.find()) {
            weekExpr = weekM.group(1);
            if (weekExpr.contains("-")) {
                String[] arr = weekExpr.split("-");
                weekRanges.add(new int[] { Integer.parseInt(arr[0]), Integer.parseInt(arr[1]) });
            } else {
                int v = Integer.parseInt(weekExpr);
                weekRanges.add(new int[] { v, v });
            }
        }
        if (!weekRanges.isEmpty()) {
            weekStart = weekRanges.get(0)[0];
            weekEnd = weekRanges.get(0)[1];
        }
        rest = restWithWeeks.replaceAll("(\\d+(?:-\\d+)?)周(?:[、,，])?", " ").replaceAll("\\s+", " ").trim();

        // 2.3 剩下的是：课程名 + [老师名] + 教室
        // 固定策略：最后一个 token = 教室（可能含“、”分隔多个）
        // 倒数第二个 token = 老师名（如果总 token 数 >= 3）
        // 其它 token 拼成课程名
        String restClean = rest.replaceAll("\\s+", " ").trim();
        java.util.regex.Pattern englishNamePat = java.util.regex.Pattern
                .compile("\\b([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+)+)\\b");
        java.util.regex.Matcher englishM = englishNamePat.matcher(restClean);
        String teacherEnglish = null;
        if (englishM.find()) {
            teacherEnglish = englishM.group(1);
            restClean = restClean.replace(teacherEnglish, " ").replaceAll("\\s+", " ").trim();
        }
        String[] tokens = restClean.split(" ");
        if (tokens.length < 2) {
            log.warn("课程片段字段不足，无法解析: {}", seg);
            return java.util.Collections.emptyList();
        }

        int classroomIdx = -1;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.matches(".*(楼|教室).*") || t.matches(".*\\d{3,}.*")) {
                classroomIdx = i;
                break;
            }
        }
        if (classroomIdx == -1)
            classroomIdx = tokens.length - 1;
        String classroomFull = tokens[classroomIdx];

        Integer teacherIdx = null;
        String teacherName = null;
        if (teacherEnglish != null) {
            teacherName = teacherEnglish;
        } else {
            int bestScore = Integer.MIN_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < tokens.length; i++) {
                if (i == classroomIdx)
                    continue;
                String t = tokens[i];
                int score = 0;
                if (t.matches("[\\p{IsHan}]{2,3}(?:[、][\\p{IsHan}]{2,3})*"))
                    score += 3;
                if (!t.isEmpty() && COMMON_SURNAME_CHARS.contains(t.substring(0, 1)))
                    score += 2;
                boolean hasCourseSuffix = false;
                for (String suf : COURSE_SUFFIXES) {
                    if (t.endsWith(suf) || t.contains(suf)) {
                        hasCourseSuffix = true;
                        break;
                    }
                }
                if (hasCourseSuffix)
                    score -= 4;
                // 修复正则中的转义，确保在 Java 字符串中正确匹配方括号
                if (t.matches(".*[0-9（）()\\[\\]].*"))
                    score -= 3;
                if (i == tokens.length - 2)
                    score += 1;
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }
            if (bestScore > 0 && bestIdx >= 0) {
                teacherIdx = bestIdx;
                teacherName = tokens[bestIdx];
            }
        }

        StringBuilder cn = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i == classroomIdx)
                continue;
            if (teacherIdx != null && i == teacherIdx)
                continue;
            if (cn.length() > 0)
                cn.append(" ");
            cn.append(tokens[i]);
        }
        String courseName = cn.toString();

        // 2.4 教室解析：当存在多个教室（用中文顿号“、”分隔）时，为每个教室生成一条排课
        String[] classroomList = classroomFull.split("、");

        java.util.List<Object[]> weekClassroomPairs = new java.util.ArrayList<>();
        Matcher wcMatcher = weekPat.matcher(restWithWeeks);
        int scanPos = 0;
        while (wcMatcher.find(scanPos)) {
            String wx = wcMatcher.group(1);
            int ws, we;
            if (wx.contains("-")) {
                String[] arr = wx.split("-");
                ws = Integer.parseInt(arr[0]);
                we = Integer.parseInt(arr[1]);
            } else {
                ws = Integer.parseInt(wx);
                we = ws;
            }
            String tail = restWithWeeks.substring(wcMatcher.end()).trim();
            String[] tailTokens = tail.isEmpty() ? new String[0] : tail.split(" ");
            String clsCandidate = null;
            for (String t : tailTokens) {
                if (t.matches(".*(楼|教室).*") || t.matches(".*\\d{3,}.*")) {
                    clsCandidate = t;
                    break;
                }
                // 遇到下一个周次则停止
                if (t.matches("\\d+(?:-\\d+)?周")) {
                    break;
                }
            }
            if (clsCandidate != null) {
                weekClassroomPairs.add(new Object[] { ws, we, clsCandidate });
            }
            scanPos = wcMatcher.end();
        }

        // 4. 维护课程基础信息
        String courseKey = courseName + "@@" + java.util.Optional.ofNullable(teacherName).orElse("");
        ScheduleParsedCourse course = courseMap.get(courseKey);
        if (course == null) {
            course = new ScheduleParsedCourse();
            course.setCourseName(courseName);
            course.setCourseCode(courseCode);
            course.setTeacherName(teacherName);
            course.setCredit(null);
            course.setColorTag(null);
            courseMap.put(courseKey, course);
        } else {
            // 如果之前没识别到代码/老师，这次有就补全
            if (course.getCourseCode() == null && courseCode != null) {
                course.setCourseCode(courseCode);
            }
            if (course.getTeacherName() == null && teacherName != null) {
                course.setTeacherName(teacherName);
            }
        }
        java.util.List<ScheduleParsedItem> resultItems = new java.util.ArrayList<>();
        if (!weekClassroomPairs.isEmpty()) {
            Integer weekOddEven = 0;
            for (Object[] pair : weekClassroomPairs) {
                int ws = (Integer) pair[0];
                int we = (Integer) pair[1];
                String cls = (String) pair[2];
                String[] clsList = cls.split("、");
                for (String one : clsList) {
                    String clsTrim = one.trim();
                    if (clsTrim.isEmpty())
                        continue;
                    ScheduleParsedItem it = new ScheduleParsedItem();
                    it.setCourseName(courseName);
                    it.setTeacherName(teacherName);
                    it.setDayOfWeek(dayOfWeek);
                    it.setSectionStart(sectionStart);
                    it.setSectionCount(sectionCount);
                    it.setWeekStart(ws);
                    it.setWeekEnd(we);
                    it.setWeekOddEven(weekOddEven);
                    it.setClassroom(clsTrim);
                    it.setCampus(null);
                    it.setRemark(null);
                    it.setRawTimeExpr(buildRawTimeExpr(ws, we, sectionStart, sectionCount, clsTrim));
                    resultItems.add(it);
                }
            }
        } else {
            if (weekRanges.isEmpty()) {
                weekRanges.add(new int[] { 1, 1 });
            }
            Integer weekOddEven = 0;
            for (String cls : classroomList) {
                String clsTrim = cls.trim();
                if (clsTrim.isEmpty())
                    continue;
                for (int[] wr : weekRanges) {
                    ScheduleParsedItem it = new ScheduleParsedItem();
                    it.setCourseName(courseName);
                    it.setTeacherName(teacherName);
                    it.setDayOfWeek(dayOfWeek);
                    it.setSectionStart(sectionStart);
                    it.setSectionCount(sectionCount);
                    it.setWeekStart(wr[0]);
                    it.setWeekEnd(wr[1]);
                    it.setWeekOddEven(weekOddEven);
                    it.setClassroom(clsTrim);
                    it.setCampus(null);
                    it.setRemark(null);
                    it.setRawTimeExpr(buildRawTimeExpr(wr[0], wr[1], sectionStart, sectionCount, clsTrim));
                    resultItems.add(it);
                }
            }
        }
        return resultItems;
    }

    /**
     * 解析 “一、二” / “五、六、七、八” 这种节次描述。
     * 返回 [sectionStart, sectionCount]
     */
    private int[] parseSectionText(String sectionText) {
        String clean = sectionText.replace("第", "").replace("节", "").trim();
        String[] parts = clean.split("、");
        List<Integer> list = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty())
                continue;
            list.add(chineseNumberToInt(p));
        }
        if (list.isEmpty()) {
            // 极端兜底
            return new int[] { 1, 2 };
        }
        Collections.sort(list);
        int start = list.get(0);
        int count = list.size();
        return new int[] { start, count };
    }

    /**
     * 中文数字（一、二、三、四、五、六、七、八、九、十、十一、十二） -> 阿拉伯数字
     */
    private int chineseNumberToInt(String cn) {
        cn = cn.trim();
        switch (cn) {
            case "一":
                return 1;
            case "二":
                return 2;
            case "三":
                return 3;
            case "四":
                return 4;
            case "五":
                return 5;
            case "六":
                return 6;
            case "七":
                return 7;
            case "八":
                return 8;
            case "九":
                return 9;
            case "十":
                return 10;
            case "十一":
                return 11;
            case "十二":
                return 12;
            default:
                // 简单处理“十X”
                if (cn.startsWith("十")) {
                    int base = 10;
                    String rest = cn.substring(1);
                    if (rest.isEmpty())
                        return base;
                    return base + chineseNumberToInt(rest);
                }
                log.warn("无法解析中文数字: {}", cn);
                return 1;
        }
    }

    private String buildRawTimeExpr(Integer weekStart, Integer weekEnd,
            Integer sectionStart, Integer sectionCount,
            String classroomFull) {
        // 周次部分
        String weekPart;
        if (weekStart != null && weekEnd != null) {
            if (Objects.equals(weekStart, weekEnd)) {
                weekPart = weekStart + "周";
            } else {
                weekPart = weekStart + "-" + weekEnd + "周";
            }
        } else {
            weekPart = "周次未知";
        }

        // 节次部分（用阿拉伯数字即可）
        String sectionPart;
        if (sectionStart != null && sectionCount != null) {
            int endSec = sectionStart + sectionCount - 1;
            if (sectionCount == 1) {
                sectionPart = "第" + sectionStart + "节";
            } else {
                sectionPart = "第" + sectionStart + "-" + endSec + "节";
            }
        } else {
            sectionPart = "节次未知";
        }

        String classroom = Optional.ofNullable(classroomFull).orElse("教室未知");
        return weekPart + "," + sectionPart + "," + classroom;
    }

    // ===================== 3. DashScope 通用调用 =====================

    private String callDashScope(JSONArray messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);

        String url = dashscopeBaseUrl.endsWith("/chat/completions")
                ? dashscopeBaseUrl
                : dashscopeBaseUrl + "/chat/completions";

        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("调用 DashScope 失败，HTTP=" + resp.getStatusCodeValue());
        }

        String result = resp.getBody();
        log.info("DashScope 原始应答：{}", result);
        if (result == null || result.isEmpty()) {
            throw new RuntimeException("DashScope 返回空响应");
        }

        JSONObject root = JSON.parseObject(result);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("DashScope 应答中没有 choices");
        }
        JSONObject choice0 = choices.getJSONObject(0);
        JSONObject message = choice0.getJSONObject("message");
        Object content = message.get("content");
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof JSONArray arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject c = arr.getJSONObject(i);
                if ("text".equals(c.getString("type"))) {
                    sb.append(c.getString("text"));
                }
            }
            return sb.toString();
        }
        throw new RuntimeException("无法从 message.content 中提取文本: " + content);
    }

    // ===================== 4. 通用工具方法 =====================

    private String toDataUrl(Path path) throws java.io.IOException {
        String mime = "image/png";
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            mime = "image/jpeg";
        }
        byte[] bytes = Files.readAllBytes(path);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mime + ";base64," + b64;
    }

    // 用于分割“星期X：”片段的内部结构体
    private static class DayBlock {
        final int dayOfWeek;
        final int startIndex;
        int endIndex;

        DayBlock(int dayOfWeek, int startIndex) {
            this.dayOfWeek = dayOfWeek;
            this.startIndex = startIndex;
        }
    }
}
