# MD05 English–Sinhala review inventory

70 app resource pairs and four proposed saved replies from the source at the commit containing the latest changes to this inventory. The app pairs come from the English and Sinhala `strings.xml` files, and the saved replies come from the server-side `SupportCopy.java` catalog. Staff publication requires second-STAFF approval. Native-speaker approval is pending. The earlier 183 approved pairs retain their original scope.

Automated linguistic review covered grammar and spelling, natural wording, financial and support terminology, EN/SI meaning and accessibility readability. Three Sinhala values were corrected: the FAQ now specifies supported message formats, publishing explicitly requires validated revisions, and the STAFF count says requests are assigned to no one. Final review incorporates six owner-specified resource corrections covering disabled users, ticket status, saved-reply loading, temporary STAFF passwords, and diagnostic report privacy. Saved reply text and formatting placeholders were preserved. Native-speaker review remains required.

## App resources

| Resource | English | Sinhala |
| --- | --- | --- |
| `admin_stats_counts` | Active users: %1$d\nDisabled users: %2$d\nStaff: %3$d\nOpen requests: %4$d\nActive SMS formats: %5$d | සක්‍රිය පරිශීලකයන්: %1$d\nඅක්‍රිය කළ පරිශීලකයන්: %2$d\nකාර්ය මණ්ඩලය: %3$d\nවිවෘත ඉල්ලීම්: %4$d\nසක්‍රිය SMS ආකෘති: %5$d |
| `faq_language` | How do I change the app language? | යෙදුමේ භාෂාව වෙනස් කරන්නේ කෙසේද? |
| `faq_language_body` | Tap Language and choose English or Sinhala. Connect to the internet to save the choice to your account. | භාෂාව ඔබා ඉංග්‍රීසි හෝ සිංහල තෝරන්න. තේරීම ඔබේ ගිණුමේ සුරැකීමට අන්තර්ජාලයට සම්බන්ධ වන්න. |
| `faq_login` | How do I sign in? | මගේ ගිණුමට පිවිසෙන්නේ කෙසේද? |
| `faq_login_body` | Sign in using the email address and password for your FINORA account. If sign-in fails, check your connection and account details. Never share your password or a one-time code. | ඔබේ FINORA ගිණුමේ විද්‍යුත් තැපැල් ලිපිනය සහ මුරපදය භාවිත කර පිවිසෙන්න. පිවිසීමට නොහැකි නම් අන්තර්ජාල සම්බන්ධතාව සහ ගිණුම් විස්තර පරීක්ෂා කරන්න. මුරපදය හෝ එක් වරක් භාවිත කරන කේත කිසිවෙකු සමඟ බෙදා නොගන්න. |
| `faq_navigation` | Where do I manage records and plans? | වාර්තා සහ සැලසුම් කළමනාකරණය කරන්නේ කොතැනින්ද? |
| `faq_privacy` | What should I keep private? | පෞද්ගලිකව තබා ගත යුත්තේ මොනවාද? |
| `faq_privacy_body` | Keep passwords, one-time codes, bank account details and full SMS messages private. FINORA checks supported bank messages on your phone. Review any extracted transaction before saving it. | මුරපද, එක් වරක් භාවිත කරන කේත, බැංකු ගිණුම් විස්තර සහ සම්පූර්ණ SMS පණිවිඩ පෞද්ගලිකව තබා ගන්න. FINORA සහාය දක්වන ආකෘතිවල බැංකු පණිවිඩ ඔබේ දුරකථනයේදීම පරීක්ෂා කරයි. පණිවිඩයෙන් හඳුනාගත් ගනුදෙනුවක් සුරැකීමට පෙර එය පරීක්ෂා කරන්න. |
| `faq_title` | Frequently asked questions | නිතර අසන ප්‍රශ්න |
| `sms_diagnostic_ack` | Acknowledge flag | ලැබුණු බව තහවුරු කරන්න |
| `sms_diagnostic_acknowledged` | Acknowledged | ලැබුණු බව තහවුරු කර ඇත |
| `sms_diagnostic_admin` | SMS diagnostics and formats | SMS දෝෂ වාර්තා සහ ආකෘති |
| `sms_diagnostic_close` | Close flag | වාර්තාව වසන්න |
| `sms_diagnostic_count` | %1$s · %2$s · Version %3$s · Reports %4$d | %1$s · %2$s · අනුවාදය %3$s · වාර්තා %4$d |
| `sms_diagnostic_escalation` | %1$s · %2$s · %3$s | %1$s · %2$s · %3$s |
| `sms_diagnostic_flag` | Flag for administrator | පරිපාලක වෙත යොමු කරන්න |
| `sms_diagnostic_open` | Open | විවෘතයි |
| `sms_diagnostic_queue` | SMS diagnostics | SMS දෝෂ වාර්තා |
| `sms_diagnostic_unknown` | Unknown | නොදනී |
| `sms_diagnostic_updated` | Change saved. | වෙනස සුරකින ලදී. |
| `sms_format_activate` | Activate approved NSB format | අනුමත NSB ආකෘතිය සක්‍රිය කරන්න |
| `sms_format_disable` | Disable approved NSB format | අනුමත NSB ආකෘතිය අක්‍රිය කරන්න |
| `sms_format_draft` | Draft approved NSB bundle revision | අනුමත NSB එකතුවේ අනුවාදයක් කෙටුම්පත් කරන්න |
| `sms_format_nsb` | Approved NSB format, version 1 | අනුමත NSB ආකෘතිය, අනුවාදය 1 |
| `sms_format_publish` | Publish validated revision | වලංගු බව තහවුරු කළ අනුවාදය ප්‍රකාශයට පත් කරන්න |
| `sms_format_published` | Published | ප්‍රකාශිතයි |
| `sms_format_revision` | NSB deployment revision %1$d · %2$s | NSB යෙදවීමේ අනුවාදය %1$d · %2$s |
| `sms_format_revision_number` | Deployment revision number | යෙදවීමේ අනුවාද අංකය |
| `sms_report_failed` | Could not confirm delivery. You can try sending the same report again. | වාර්තාව ලැබුණු බව තහවුරු කළ නොහැක. එම වාර්තාව නැවත යැවීමට උත්සාහ කළ හැක. |
| `sms_report_preview` | Bank: %1$s\nFormat version: unknown\nError code: %2$s\n\nOnly these details will be sent in this report. Your original SMS and financial details will not be included. | බැංකුව: %1$s\nආකෘති අනුවාදය: නොදනී\nදෝෂ කේතය: %2$s\n\nමෙම වාර්තාව සමඟ යවනු ලබන්නේ මෙම තොරතුරු පමණි. ඔබේ මුල් SMS පණිවිඩය සහ මූල්‍ය තොරතුරු මෙම වාර්තාවට ඇතුළත් නොවේ. |
| `sms_report_problem` | Report problem | ගැටලුව වාර්තා කරන්න |
| `sms_report_send` | Send report | වාර්තාව යවන්න |
| `sms_report_sent` | Report sent. | වාර්තාව යවන ලදී. |
| `staff_account_summary` | %1$s · %2$s | %1$s · %2$s |
| `staff_activate` | Set up staff access | කාර්ය මණ්ඩල ප්‍රවේශය සකසන්න |
| `staff_activation_done` | Password changed. Go back and sign in with your new password. | මුරපදය වෙනස් කරන ලදී. ආපසු ගොස් නව මුරපදයෙන් පිවිසෙන්න. |
| `staff_activation_failed` | Could not confirm setup. Check the details or try signing in with your new password. | ප්‍රවේශය සැකසූ බව තහවුරු කළ නොහැක. විස්තර පරීක්ෂා කරන්න හෝ නව මුරපදයෙන් පිවිසීමට උත්සාහ කරන්න. |
| `staff_create` | Create staff account | කාර්ය මණ්ඩල ගිණුමක් සාදන්න |
| `staff_disable` | Disable staff account | කාර්ය මණ්ඩල ගිණුම අක්‍රිය කරන්න |
| `staff_enable` | Enable staff account | කාර්ය මණ්ඩල ගිණුම සක්‍රිය කරන්න |
| `staff_full_name` | Full name | සම්පූර්ණ නම |
| `staff_manage` | Manage staff access | කාර්ය මණ්ඩල ප්‍රවේශය කළමනාකරණය කරන්න |
| `staff_new_password` | New password | නව මුරපදය |
| `staff_queue_counts` | My open tickets: %1$d\nUnassigned tickets: %2$d | මට පැවරූ විවෘත සහාය ඉල්ලීම්: %1$d\nකිසිවෙකුට පවරා නැති සහාය ඉල්ලීම්: %2$d |
| `staff_reissue` | Replace temporary password | තාවකාලික මුරපදය ප්‍රතිස්ථාපනය කරන්න |
| `staff_secret_once` | %1$s\n\nShown once. Share it only through the approved secure channel. It expires in 24 hours. | %1$s\n\nඑක් වරක් පමණක් පෙන්වයි. අනුමත ආරක්ෂිත මාර්ගයෙන් පමණක් බෙදා ගන්න. පැය 24කින් කල් ඉකුත් වේ. |
| `staff_temporary` | Temporary staff password | කාර්ය මණ්ඩලය සඳහා තාවකාලික මුරපදය |
| `support_active` | Active | සක්‍රියයි |
| `support_approve` | Approve reply | පිළිතුර අනුමත කරන්න |
| `support_catalog` | Saved replies | සුරැකි පිළිතුරු |
| `support_claim` | Claim request | ඉල්ලීම භාර ගන්න |
| `support_close` | Close request | ඉල්ලීම වසන්න |
| `support_closed` | Closed | වසා ඇත |
| `support_create` | New support request | නව සහාය ඉල්ලීම |
| `support_draft` | Draft a saved reply | සුරැකි පිළිතුරක් කෙටුම්පත් කරන්න |
| `support_draft_state` | Draft | කෙටුම්පත |
| `support_in_progress` | In progress | කටයුතු සිදු වෙමින් පවතී |
| `support_language` | Language | භාෂාව |
| `support_load_replies` | Load saved replies | සුරැකි පිළිතුරු ලබාගන්න |
| `support_navigation` | Navigation | යෙදුම තුළ ගමන් කිරීම |
| `support_no_reply` | No approved replies are loaded. Load the saved replies and try again. | අනුමත පිළිතුරු තවම ලබාගෙන නැත. සුරැකි පිළිතුරු ලබාගෙන නැවත උත්සාහ කරන්න. |
| `support_privacy` | Choose a help topic. FINORA sends only the standard request for that topic. Do not share passwords, codes or SMS messages. | සහාය මාතෘකාවක් තෝරන්න. FINORA යවන්නේ එම මාතෘකාවට අදාළ සම්මත ඉල්ලීම පමණි. මුරපද, කේත හෝ SMS පණිවිඩ බෙදා නොගන්න. |
| `support_replies` | View replies | පිළිතුරු බලන්න |
| `support_reply` | Send a saved reply | සුරැකි පිළිතුරක් යවන්න |
| `support_retire` | Retire reply | පිළිතුර භාවිතයෙන් ඉවත් කරන්න |
| `support_retired` | Retired | භාවිතයෙන් ඉවත් කර ඇත |
| `support_sign_in` | Sign-in | පිවිසීම |
| `support_sms` | SMS permission | SMS අවසරය |
| `support_ticket_summary` | Request %1$d · %2$s · %3$s | ඉල්ලීම %1$d · %2$s · %3$s |
| `support_tickets` | Support requests | සහාය ඉල්ලීම් |

## Proposed saved replies

| Topic | English | Sinhala |
| --- | --- | --- |
| Sign-in | Check your connection and try signing in again. | ඔබේ අන්තර්ජාල සම්බන්ධතාව පරීක්ෂා කර නැවත පිවිසීමට උත්සාහ කරන්න. |
| Navigation | Open Records to review transactions, or Plan to manage budgets and savings goals. | ගනුදෙනු පරීක්ෂා කිරීමට වාර්තා විවෘත කරන්න. අයවැය සහ ඉතිරිකිරීමේ ඉලක්ක කළමනාකරණය කිරීමට සැලසුම විවෘත කරන්න. |
| SMS permission | SMS access is optional. You can enter a transaction manually or import a CSV file without it. | SMS ප්‍රවේශය අත්‍යවශ්‍ය නොවේ. SMS ප්‍රවේශයට අවසර නොදී ගනුදෙනුවක් අතින් ඇතුළත් කිරීමට හෝ CSV ගොනුවක් ආයාත කිරීමට හැකිය. |
| Language | Open Language and choose English or Sinhala. Connect to the internet to save the choice to your account. | භාෂාව විවෘත කර ඉංග්‍රීසි හෝ සිංහල තෝරන්න. තේරීම ඔබේ ගිණුමට සුරැකීමට අන්තර්ජාලයට සම්බන්ධ වන්න. |
