function xgIconTips(plugin, textKey, isShow) {
  try {
    return ' <div class="xg-tips '.concat(isShow ? "hide" : " ", '" lang-key="').concat(plugin.i18nKeys[textKey], '">\n    ').concat(plugin.i18n[textKey], "\n    </div>");
  } catch (e) {
    return '<div class="xg-tips hide"></div>';
  }
}
export { xgIconTips };
