package com.dexform.app.core.autofill

import android.webkit.WebView
import com.dexform.app.AppContainer
import com.dexform.app.data.db.FieldValueEntity
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

data class AutofillResult(
    val filledCount: Int,
    val filledFields: List<String>,
    val isAiPowered: Boolean = false,
    val skippedCount: Int = 0
)

data class AutofillRule(
    val label: String,
    val value: String,
    val keywords: List<String>
)

object FormAutofillEngine {

    /**
     * Builds list of autofill candidates from active profile, dynamic bio, and sub-bio documents.
     */
    suspend fun prepareAutofillRules(
        container: AppContainer,
        profile: UserProfileEntity
    ): List<AutofillRule> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<AutofillRule>()

        fun addRule(label: String, value: String, vararg syns: String) {
            val v = value.trim()
            if (v.isNotBlank()) {
                val kwList = (listOf(label.lowercase()) + syns.map { it.lowercase() }).distinct()
                rules.add(AutofillRule(label = label, value = v, keywords = kwList))
            }
        }

        // 1. Core Profile Details
        addRule("Full Name", profile.name, "candidate name", "applicant name", "name", "full_name", "first name", "नाम")
        addRule("Father's Name", profile.fatherName, "father name", "father's name", "guardian name", "पिता का नाम", "s/o", "d/o")
        addRule("Mother's Name", profile.motherName, "mother name", "mother's name", "माता का नाम")
        addRule("Date of Birth", profile.dob, "dob", "birth date", "date of birth", "d.o.b", "जन्म तिथि")
        addRule("Gender", profile.gender, "sex", "gender", "लिंग")
        addRule("Category", profile.category, "caste", "category", "social category", "community", "वर्ग")
        addRule("Mobile Number", profile.mobile, "mobile", "phone", "contact", "mobile no", "contact no", "cell", "मोबाइल")
        addRule("Email Address", profile.email, "email", "e-mail", "email id", "mail", "ईमेल")
        addRule("Address", profile.address, "permanent address", "residential address", "address line", "full address", "पता")
        addRule("ID Number", profile.idNumber, "aadhaar", "aadhaar number", "pan", "pan number", "id proof", "identity number")

        // 2. 10th Academic Info (with percentage & division reasoning)
        addRule("10th Roll Number", profile.tenthRollNo, "10th roll", "matric roll", "high school roll", "secondary roll")
        addRule("10th Registration No", profile.tenthRegNo, "10th reg", "matric reg", "10th enrollment", "high school reg")
        addRule("10th Passing Year", profile.tenthPassingYear, "10th year", "matric year", "10th pass year", "year of passing 10th")
        addRule("10th Board", profile.tenthBoard, "10th board", "matric board", "high school board", "secondary board")
        addRule("10th Marks / %", profile.tenthMarks, "10th marks", "matric marks", "10th aggregate")

        val tenthReasoning = derivePercentageAndDivision(profile.tenthMarks, profile.tenthDivision)
        if (tenthReasoning.percentage.isNotBlank()) {
            addRule("10th Percentage", tenthReasoning.percentage, "10th percentage", "10th percent", "matric percentage", "high school %", "10th %")
        }
        if (tenthReasoning.division.isNotBlank()) {
            addRule("10th Division", tenthReasoning.division, "10th division", "matric division", "10th grade")
        }

        // 3. 12th Academic Info (with percentage & division reasoning)
        addRule("12th Roll Number", profile.twelfthRollNo, "12th roll", "inter roll", "intermediate roll", "higher secondary roll", "sr secondary roll")
        addRule("12th Registration No", profile.twelfthRegNo, "12th reg", "inter reg", "12th enrollment")
        addRule("12th Passing Year", profile.twelfthPassingYear, "12th year", "inter year", "12th pass year", "year of passing 12th")
        addRule("12th Board", profile.twelfthBoard, "12th board", "inter board", "higher secondary board", "intermediate board")
        addRule("12th Marks / %", profile.twelfthMarks, "12th marks", "inter marks", "12th aggregate")

        val twelfthReasoning = derivePercentageAndDivision(profile.twelfthMarks, profile.twelfthDivision)
        if (twelfthReasoning.percentage.isNotBlank()) {
            addRule("12th Percentage", twelfthReasoning.percentage, "12th percentage", "12th percent", "inter percentage", "intermediate %", "12th %")
        }
        if (twelfthReasoning.division.isNotBlank()) {
            addRule("12th Division", twelfthReasoning.division, "12th division", "inter division", "12th grade")
        }

        // 4. Dynamic Bio Values (docId == null)
        val bioValues = container.fieldValueDao.getBioValuesOnce(profile.id)
        val defsList = container.fieldDefDao.getApprovedDefs()
        val defMap = defsList.associateBy { it.key }

        for (bv in bioValues) {
            val def = defMap[bv.fieldKey]
            val label = def?.label ?: bv.fieldKey.substringAfterLast('.').replace('_', ' ').capitalizeWords()
            val extraSyns = parseSynonyms(def?.synonymsJson)
            addRule(label, bv.valueEnc, *extraSyns.toTypedArray())
        }

        // 5. Saved Document Records & Sub-Bio Values
        val docRecords = container.docRecordDao.getDocsForProfileOnce(profile.id)
        for (doc in docRecords) {
            val docValues = container.fieldValueDao.getDocValuesOnce(profile.id, doc.id)
            for (dv in docValues) {
                val def = defMap[dv.fieldKey]
                val fieldLabel = def?.label ?: dv.fieldKey.substringAfterLast('.').replace('_', ' ').capitalizeWords()
                val fullLabel = "${doc.title} - $fieldLabel"
                val extraSyns = parseSynonyms(def?.synonymsJson)
                val docSpecificSyns = extraSyns.map { "${doc.docType.lowercase()} $it" } + extraSyns
                addRule(fullLabel, dv.valueEnc, *docSpecificSyns.toTypedArray())
            }
        }

        rules
    }

    private data class AcademicReasoning(val percentage: String, val division: String)

    private fun derivePercentageAndDivision(rawMarks: String, existingDivision: String): AcademicReasoning {
        var percentage = ""
        var division = existingDivision.trim()

        if (rawMarks.contains("%")) {
            percentage = rawMarks.trim()
            val pctNum = rawMarks.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()
            if (division.isBlank() && pctNum != null) {
                division = when {
                    pctNum >= 60.0 -> "1st Division"
                    pctNum >= 45.0 -> "2nd Division"
                    pctNum >= 33.0 -> "3rd Division"
                    else -> "Passed"
                }
            }
        } else {
            // Check fraction like "425/500" or "425 / 500"
            val fractionMatch = Regex("""(\d{2,4})\s*/\s*(\d{2,4})""").find(rawMarks)
            if (fractionMatch != null) {
                val num1 = fractionMatch.groupValues[1].toDoubleOrNull()
                val num2 = fractionMatch.groupValues[2].toDoubleOrNull()
                if (num1 != null && num2 != null && num2 > 0) {
                    val pct = (num1 / num2) * 100.0
                    percentage = String.format(Locale.US, "%.2f%%", pct)
                    if (division.isBlank()) {
                        division = when {
                            pct >= 60.0 -> "1st Division"
                            pct >= 45.0 -> "2nd Division"
                            pct >= 33.0 -> "3rd Division"
                            else -> "Passed"
                        }
                    }
                }
            } else {
                // If marks is a single number like "82.5" or "425"
                val num = rawMarks.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()
                if (num != null) {
                    if (num <= 100.0) {
                        percentage = "$num%"
                        if (division.isBlank()) {
                            division = when {
                                num >= 60.0 -> "1st Division"
                                num >= 45.0 -> "2nd Division"
                                num >= 33.0 -> "3rd Division"
                                else -> "Passed"
                            }
                        }
                    } else if (num in 150.0..600.0) {
                        // Assume out of 500
                        val pct = (num / 500.0) * 100.0
                        percentage = String.format(Locale.US, "%.2f%%", pct)
                        if (division.isBlank()) {
                            division = when {
                                pct >= 60.0 -> "1st Division"
                                pct >= 45.0 -> "2nd Division"
                                pct >= 33.0 -> "3rd Division"
                                else -> "Passed"
                            }
                        }
                    }
                }
            }
        }

        return AcademicReasoning(percentage, division)
    }

    private fun parseSynonyms(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Executes AI-Powered Cognitive Autofill:
     * 1. Scrapes the web page DOM schema (inputs, labels, placeholders, select options).
     * 2. If local AI model is loaded, passes the form schema + user's complete context window to AI.
     * 3. AI reasons about tricky dropdowns, computed percentage/division, and maps values.
     * 4. Injects mapped values into the DOM.
     * 5. Falls back seamlessly to rule-based matching if AI is not loaded or times out.
     */
    suspend fun executeAiCognitiveAutofill(
        webView: WebView,
        container: AppContainer,
        profile: UserProfileEntity,
        overwriteExisting: Boolean = false
    ): AutofillResult = withContext(Dispatchers.Main) {
        // Extract DOM schema
        val formSchema = extractFormSchema(webView)

        if (container.localAiManager.isLoaded && formSchema.length() > 0) {
            try {
                val profileContext = withContext(Dispatchers.IO) {
                    buildProfileContextForAi(container, profile)
                }

                val aiPrompt = """
                You are a smart AI Form Autofill Agent.
                USER PROFILE CONTEXT:
                $profileContext

                FORM INPUTS TO FILL:
                ${formSchema.toString()}

                TASK:
                Match each input field with the user's data.
                - If the form asks for Percentage and only Marks are available, compute it.
                - If the form asks for Division, output '1st Division', '2nd Division', etc.
                - If the input is a 'select' dropdown, choose the closest matching option text from its options.
                - Output strict JSON only:
                {"mappings": [{"id": "...", "value": "..."}]}
                """.trimIndent()

                val aiResponse = withContext(Dispatchers.IO) {
                    container.localAiManager.generateOneShot(aiPrompt, timeoutMs = 20_000L)
                }

                val jsonMatch = Regex("""(?s)\{\s*"mappings"\s*:\s*\[.*?\]\s*\}""").find(aiResponse)
                if (jsonMatch != null) {
                    val root = JSONObject(jsonMatch.value)
                    val mappings = root.optJSONArray("mappings")
                    if (mappings != null && mappings.length() > 0) {
                        val injectedCount = injectAiMappings(webView, mappings, overwriteExisting)
                        if (injectedCount > 0) {
                            val filledNames = (0 until mappings.length()).mapNotNull { i ->
                                val m = mappings.optJSONObject(i)
                                m?.optString("id")
                            }
                            return@withContext AutofillResult(
                                filledCount = injectedCount,
                                filledFields = filledNames,
                                isAiPowered = true
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to rule engine
            }
        }

        // Fast fallback to deterministic rule engine
        val rules = prepareAutofillRules(container, profile)
        executeAutofill(webView, rules, overwriteExisting)
    }

    private suspend fun extractFormSchema(webView: WebView): JSONArray = suspendCancellableCoroutine { cont ->
        val js = """
        (function() {
            var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=file]):not([type=checkbox]):not([type=radio]), textarea, select'));
            return JSON.stringify(inputs.slice(0, 30).map(function(el, idx) {
                var id = el.id || ('dex_input_' + idx);
                el.setAttribute('data-dex-id', id);
                var labelText = '';
                if (el.labels && el.labels.length) labelText = el.labels[0].innerText || el.labels[0].textContent || '';
                var opts = [];
                if (el.tagName === 'SELECT') {
                    for (var i = 0; i < el.options.length; i++) {
                        var t = el.options[i].text || el.options[i].value;
                        if (t) opts.push(t.trim());
                    }
                }
                return {
                    id: id,
                    name: el.name || '',
                    tag: el.tagName.toLowerCase(),
                    label: labelText.trim(),
                    placeholder: el.placeholder || '',
                    options: opts.slice(0, 8)
                };
            }));
        })()
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            try {
                val unescaped = if (raw != null && raw.startsWith("\"") && raw.endsWith("\"")) {
                    org.json.JSONTokener(raw).nextValue() as? String ?: raw
                } else raw
                cont.resume(JSONArray(unescaped ?: "[]"))
            } catch (_: Exception) {
                cont.resume(JSONArray())
            }
        }
    }

    private suspend fun injectAiMappings(
        webView: WebView,
        mappings: JSONArray,
        overwrite: Boolean
    ): Int = suspendCancellableCoroutine { cont ->
        val js = """
        (function(mappings, overwrite) {
            var count = 0;
            for (var i = 0; i < mappings.length; i++) {
                var m = mappings[i];
                if (!m.id || !m.value) continue;
                var el = document.querySelector('[data-dex-id="' + m.id + '"]') || document.getElementById(m.id);
                if (!el) continue;
                var curVal = (el.tagName === 'SELECT') ? (el.value || '') : (el.value || '').trim();
                if (!overwrite && curVal.length > 0 && curVal !== '0') continue;

                if (el.tagName === 'SELECT') {
                    var normVal = m.value.toLowerCase().trim();
                    for (var optIdx = 0; optIdx < el.options.length; optIdx++) {
                        var optText = el.options[optIdx].text.toLowerCase().trim();
                        var optVal = el.options[optIdx].value.toLowerCase().trim();
                        if (optText === normVal || optVal === normVal || optText.indexOf(normVal) !== -1 || normVal.indexOf(optText) !== -1) {
                            el.selectedIndex = optIdx;
                            break;
                        }
                    }
                } else {
                    el.value = m.value;
                }

                el.dispatchEvent(new Event('focus', { bubbles: true }));
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                el.dispatchEvent(new Event('blur', { bubbles: true }));

                var prevBg = el.style.backgroundColor;
                var prevOutline = el.style.outline;
                el.style.backgroundColor = 'rgba(34, 197, 94, 0.2)';
                el.style.outline = '2px solid #22c55e';
                setTimeout(function(targetEl, bg, ol) {
                    targetEl.style.backgroundColor = bg;
                    targetEl.style.outline = ol;
                }, 3000, el, prevBg, prevOutline);

                count++;
            }
            return count;
        })(${mappings.toString()}, $overwrite);
        """.trimIndent()

        webView.evaluateJavascript(js) { res ->
            val num = res?.toIntOrNull() ?: 0
            cont.resume(num)
        }
    }

    private suspend fun buildProfileContextForAi(container: AppContainer, profile: UserProfileEntity): String = withContext(Dispatchers.IO) {
        val bioValues = container.fieldValueDao.getBioValuesOnce(profile.id)
        val docRecords = container.docRecordDao.getDocsForProfileOnce(profile.id)
        val defMap = container.fieldDefDao.getApprovedDefs().associateBy { it.key }

        buildString {
            appendLine("Name: ${profile.name}")
            if (profile.fatherName.isNotBlank()) appendLine("Father's Name: ${profile.fatherName}")
            if (profile.motherName.isNotBlank()) appendLine("Mother's Name: ${profile.motherName}")
            if (profile.dob.isNotBlank()) appendLine("DOB: ${profile.dob}")
            if (profile.gender.isNotBlank()) appendLine("Gender: ${profile.gender}")
            if (profile.category.isNotBlank()) appendLine("Category: ${profile.category}")
            if (profile.mobile.isNotBlank()) appendLine("Mobile: ${profile.mobile}")
            if (profile.email.isNotBlank()) appendLine("Email: ${profile.email}")
            if (profile.address.isNotBlank()) appendLine("Address: ${profile.address}")
            if (profile.idNumber.isNotBlank()) appendLine("ID/Aadhaar/PAN: ${profile.idNumber}")

            if (profile.tenthRollNo.isNotBlank() || profile.tenthMarks.isNotBlank()) {
                appendLine("10th Academic: Roll=${profile.tenthRollNo}, Year=${profile.tenthPassingYear}, Board=${profile.tenthBoard}, Marks=${profile.tenthMarks}, Division=${profile.tenthDivision}")
            }
            if (profile.twelfthRollNo.isNotBlank() || profile.twelfthMarks.isNotBlank()) {
                appendLine("12th Academic: Roll=${profile.twelfthRollNo}, Year=${profile.twelfthPassingYear}, Board=${profile.twelfthBoard}, Marks=${profile.twelfthMarks}, Division=${profile.twelfthDivision}")
            }
            for (bv in bioValues) {
                val label = defMap[bv.fieldKey]?.label ?: bv.fieldKey.substringAfterLast('.')
                appendLine("$label: ${bv.valueEnc}")
            }
            for (doc in docRecords) {
                val docVals = container.fieldValueDao.getDocValuesOnce(profile.id, doc.id)
                val valsSummary = docVals.joinToString(", ") { "${defMap[it.fieldKey]?.label ?: it.fieldKey}=${it.valueEnc}" }
                appendLine("Document: ${doc.title} ($valsSummary)")
            }
        }
    }

    /**
     * Executes fast rule-based autofill inside WebView with the provided rules.
     */
    suspend fun executeAutofill(
        webView: WebView,
        rules: List<AutofillRule>,
        overwriteExisting: Boolean = false
    ): AutofillResult = withContext(Dispatchers.Main) {
        if (rules.isEmpty()) return@withContext AutofillResult(0, emptyList())

        // Build JSON payload of rules
        val jsonRules = JSONArray().apply {
            for (rule in rules) {
                put(JSONObject().apply {
                    put("label", rule.label)
                    put("value", rule.value)
                    put("keywords", JSONArray(rule.keywords))
                })
            }
        }

        val jsCode = """
(function(rules, overwrite) {
    try {
        var filledCount = 0;
        var filledLabels = [];
        var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=file]):not([type=checkbox]):not([type=radio]), textarea, select'));
        
        function getFieldHints(el) {
            var tokens = [];
            if (el.name) tokens.push(el.name);
            if (el.id) tokens.push(el.id);
            if (el.placeholder) tokens.push(el.placeholder);
            if (el.getAttribute('aria-label')) tokens.push(el.getAttribute('aria-label'));
            if (el.getAttribute('title')) tokens.push(el.getAttribute('title'));
            
            // Associated labels
            if (el.labels && el.labels.length) {
                for (var i = 0; i < el.labels.length; i++) {
                    tokens.push(el.labels[i].innerText || el.labels[i].textContent || '');
                }
            }
            if (el.id) {
                var lbl = document.querySelector('label[for="' + el.id + '"]');
                if (lbl) tokens.push(lbl.innerText || lbl.textContent || '');
            }
            
            // Previous text sibling or parent label
            var p = el.previousElementSibling;
            if (p && (p.tagName === 'LABEL' || p.tagName === 'SPAN' || p.tagName === 'DIV' || p.tagName === 'B' || p.tagName === 'STRONG')) {
                tokens.push(p.innerText || p.textContent || '');
            }
            var parent = el.parentElement;
            if (parent && parent.tagName === 'TD') {
                var prevTd = parent.previousElementSibling;
                if (prevTd) tokens.push(prevTd.innerText || prevTd.textContent || '');
            }
            
            return tokens.join(' ').toLowerCase().replace(/[^a-z0-9\s]/g, ' ');
        }
        
        function setElementValue(el, val) {
            if (el.tagName === 'SELECT') {
                var normVal = val.toLowerCase().trim();
                var found = false;
                for (var i = 0; i < el.options.length; i++) {
                    var optText = el.options[i].text.toLowerCase().trim();
                    var optVal = el.options[i].value.toLowerCase().trim();
                    if (optText === normVal || optVal === normVal || optText.indexOf(normVal) !== -1 || normVal.indexOf(optText) !== -1) {
                        el.selectedIndex = i;
                        found = true;
                        break;
                    }
                }
                if (!found && normVal.length === 1) { // e.g. M / F
                    for (var i = 0; i < el.options.length; i++) {
                        if (el.options[i].value.toLowerCase().startsWith(normVal) || el.options[i].text.toLowerCase().startsWith(normVal)) {
                            el.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                }
            } else {
                el.value = val;
            }
            
            // Dispatch events for modern frameworks (React, Angular, Vue)
            el.dispatchEvent(new Event('focus', { bubbles: true }));
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            el.dispatchEvent(new Event('blur', { bubbles: true }));
            
            // Subtle visual highlight
            var prevBg = el.style.backgroundColor;
            var prevOutline = el.style.outline;
            el.style.backgroundColor = 'rgba(34, 197, 94, 0.15)';
            el.style.outline = '2px solid #22c55e';
            setTimeout(function() {
                el.style.backgroundColor = prevBg;
                el.style.outline = prevOutline;
            }, 3000);
        }
        
        for (var idx = 0; idx < inputs.length; idx++) {
            var input = inputs[idx];
            var curVal = (input.tagName === 'SELECT') ? (input.value || '') : (input.value || '').trim();
            if (!overwrite && curVal.length > 0 && curVal !== '0') {
                continue; // Skip already filled inputs
            }
            
            var hints = getFieldHints(input);
            if (!hints || hints.length < 2) continue;
            
            var matchedRule = null;
            var bestScore = 0;
            
            for (var r = 0; r < rules.length; r++) {
                var rule = rules[r];
                for (var k = 0; k < rule.keywords.length; k++) {
                    var kw = rule.keywords[k].trim();
                    if (kw.length >= 2 && hints.indexOf(kw) !== -1) {
                        var score = kw.length;
                        if (score > bestScore) {
                            bestScore = score;
                            matchedRule = rule;
                        }
                    }
                }
            }
            
            if (matchedRule && bestScore > 0) {
                setElementValue(input, matchedRule.value);
                filledCount++;
                if (filledLabels.indexOf(matchedRule.label) === -1) {
                    filledLabels.push(matchedRule.label);
                }
            }
        }
        
        return JSON.stringify({
            filledCount: filledCount,
            filledFields: filledLabels
        });
    } catch(err) {
        return JSON.stringify({
            filledCount: 0,
            filledFields: [],
            error: err.toString()
        });
    }
})(${jsonRules.toString()}, ${overwriteExisting});
        """.trimIndent()

        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(jsCode) { rawResponse ->
                try {
                    val unescaped = if (rawResponse != null && rawResponse.startsWith("\"") && rawResponse.endsWith("\"")) {
                        org.json.JSONTokener(rawResponse).nextValue() as? String ?: rawResponse
                    } else rawResponse

                    if (unescaped.isNullOrBlank() || unescaped == "null") {
                        continuation.resume(AutofillResult(0, emptyList()))
                        return@evaluateJavascript
                    }

                    val json = JSONObject(unescaped)
                    val count = json.optInt("filledCount", 0)
                    val arr = json.optJSONArray("filledFields")
                    val fields = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            fields.add(arr.getString(i))
                        }
                    }
                    continuation.resume(AutofillResult(filledCount = count, filledFields = fields, isAiPowered = false))
                } catch (e: Exception) {
                    continuation.resume(AutofillResult(0, emptyList()))
                }
            }
        }
    }
}
