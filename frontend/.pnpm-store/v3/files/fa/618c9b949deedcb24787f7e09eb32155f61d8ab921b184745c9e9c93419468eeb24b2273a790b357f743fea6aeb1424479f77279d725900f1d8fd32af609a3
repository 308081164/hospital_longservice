import { toConsumableArray as _toConsumableArray } from "../_virtual/_rollupPluginBabelHelpers.js";
import util from "../utils/util.js";
import EN from "./en.js";
var XGI18nLang = {
  lang: {},
  langKeys: [],
  textKeys: []
};
function deepMerge(dst, src) {
  Object.keys(src).forEach(function(key) {
    var _s = util.typeOf(src[key]);
    var _t = util.typeOf(dst[key]);
    if (_s === "Array") {
      var _dst$key;
      if (_t !== "Array") {
        dst[key] = [];
      }
      (_dst$key = dst[key]).push.apply(_dst$key, _toConsumableArray(src[key]));
    } else if (_s === "Object") {
      if (_t !== "Object") {
        dst[key] = {};
      }
      deepMerge(dst[key], src[key]);
    } else {
      dst[key] = src[key];
    }
  });
  return dst;
}
function updateKeys() {
  Object.keys(XGI18nLang.lang.en).map(function(key) {
    XGI18nLang.textKeys[key] = key;
  });
}
function extend(i18nTextList, i18nLangs) {
  var ext = [];
  if (!i18nLangs) {
    i18nLangs = XGI18nLang;
  }
  if (!i18nLangs.lang) {
    return;
  }
  if (util.typeOf(i18nTextList) !== "Array") {
    ext = Object.keys(i18nTextList).map(function(lang2) {
      var keyLang = lang2 === "zh" ? "zh-cn" : lang2;
      return {
        LANG: keyLang,
        TEXT: i18nTextList[lang2]
      };
    });
  } else {
    ext = i18nTextList;
  }
  var _i18nLangs = i18nLangs, lang = _i18nLangs.lang;
  ext.map(function(item) {
    if (item.LANG === "zh") {
      item.LANG = "zh-cn";
    }
    if (!lang[item.LANG]) {
      use(item, i18nLangs);
    } else {
      deepMerge(lang[item.LANG] || {}, item.TEXT || {});
    }
  });
  updateKeys();
}
function use(langData, i18nLangs) {
  var _clang = langData.LANG;
  if (!i18nLangs) {
    i18nLangs = XGI18nLang;
  }
  if (!i18nLangs.lang) {
    return;
  }
  var texts = langData.TEXT || {};
  if (_clang === "zh") {
    _clang = "zh-cn";
  }
  if (!i18nLangs.lang[_clang]) {
    i18nLangs.langKeys.push(_clang);
    i18nLangs.lang[_clang] = texts;
  } else {
    deepMerge(i18nLangs.lang[_clang], texts);
  }
  updateKeys();
}
function init(id) {
  var _ret$langKeys;
  var ret = {
    lang: {},
    langKeys: [],
    textKeys: {},
    pId: id
  };
  deepMerge(ret.lang, XGI18nLang.lang);
  (_ret$langKeys = ret.langKeys).push.apply(_ret$langKeys, _toConsumableArray(XGI18nLang.langKeys));
  deepMerge(ret.textKeys, XGI18nLang.textKeys);
  return ret;
}
use(EN);
var I18N = {
  get textKeys() {
    return XGI18nLang.textKeys;
  },
  get langKeys() {
    return XGI18nLang.langKeys;
  },
  get lang() {
    var ret = {};
    XGI18nLang.langKeys.map(function(key) {
      ret[key] = XGI18nLang.lang[key];
    });
    if (XGI18nLang.lang["zh-cn"]) {
      ret.zh = XGI18nLang.lang["zh-cn"] || {};
    }
    return ret;
  },
  extend,
  use,
  init
};
export { I18N as default };
