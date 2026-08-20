import { typeof as _typeof, toConsumableArray as _toConsumableArray } from "../_virtual/_rollupPluginBabelHelpers.js";
import XG_DEBUG from "./debug.js";
import XgplayerTimeRange from "./xgplayerTimeRange.js";
var util = {};
util.createDom = function() {
  var el = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : "div";
  var tpl = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : "";
  var attrs = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {};
  var cname = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : "";
  var dom = document.createElement(el);
  dom.className = cname;
  dom.innerHTML = tpl;
  Object.keys(attrs).forEach(function(item) {
    var key = item;
    var value = attrs[item];
    if (el === "video" || el === "audio" || el === "live-video") {
      if (value) {
        dom.setAttribute(key, value);
      }
    } else {
      dom.setAttribute(key, value);
    }
  });
  return dom;
};
util.createDomFromHtml = function(html) {
  var attrs = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
  var classname = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : "";
  try {
    var doc = document.createElement("div");
    doc.innerHTML = html;
    var dom = doc.children;
    doc = null;
    if (dom.length > 0) {
      dom = dom[0];
      classname && util.addClass(dom, classname);
      if (attrs) {
        Object.keys(attrs).forEach(function(key) {
          dom.setAttribute(key, attrs[key]);
        });
      }
      return dom;
    }
    return null;
  } catch (err) {
    XG_DEBUG.logError("util.createDomFromHtml", err);
    return null;
  }
};
util.hasClass = function(el, className) {
  if (!el || !className) {
    return false;
  }
  try {
    return Array.prototype.some.call(el.classList, function(item) {
      return item === className;
    });
  } catch (e) {
    var orgClassName = el.className && _typeof(el.className) === "object" ? el.getAttribute("class") : el.className;
    return orgClassName && !!orgClassName.match(new RegExp("(\\s|^)" + className + "(\\s|$)"));
  }
};
util.addClass = function(el, className) {
  if (!el || !className) {
    return;
  }
  try {
    className.replace(/(^\s+|\s+$)/g, "").split(/\s+/g).forEach(function(item) {
      item && el.classList.add(item);
    });
  } catch (e) {
    if (!util.hasClass(el, className)) {
      if (el.className && _typeof(el.className) === "object") {
        el.setAttribute("class", el.getAttribute("class") + " " + className);
      } else {
        el.className += " " + className;
      }
    }
  }
};
util.removeClass = function(el, className) {
  if (!el || !className) {
    return;
  }
  try {
    className.replace(/(^\s+|\s+$)/g, "").split(/\s+/g).forEach(function(item) {
      item && el.classList.remove(item);
    });
  } catch (e) {
    if (util.hasClass(el, className)) {
      className.split(/\s+/g).forEach(function(item) {
        var reg = new RegExp("(\\s|^)" + item + "(\\s|$)");
        if (el.className && _typeof(el.className) === "object") {
          el.setAttribute("class", el.getAttribute("class").replace(reg, " "));
        } else {
          el.className = el.className.replace(reg, " ");
        }
      });
    }
  }
};
util.toggleClass = function(el, className) {
  if (!el) {
    return;
  }
  className.split(/\s+/g).forEach(function(item) {
    if (util.hasClass(el, item)) {
      util.removeClass(el, item);
    } else {
      util.addClass(el, item);
    }
  });
};
util.classNames = function() {
  var _arguments = arguments;
  var classname = [];
  var _loop = function _loop2(i2) {
    if (util.typeOf(_arguments[i2]) === "String") {
      classname.push(_arguments[i2]);
    } else if (util.typeOf(_arguments[i2]) === "Object") {
      Object.keys(_arguments[i2]).map(function(key) {
        if (_arguments[i2][key]) {
          classname.push(key);
        }
      });
    }
  };
  for (var i = 0; i < arguments.length; i++) {
    _loop(i);
  }
  return classname.join(" ");
};
util.findDom = function() {
  var el = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : document;
  var sel = arguments.length > 1 ? arguments[1] : void 0;
  var dom;
  try {
    dom = el.querySelector(sel);
  } catch (e) {
    XG_DEBUG.logError("util.findDom", e);
    if (sel.indexOf("#") === 0) {
      dom = el.getElementById(sel.slice(1));
    }
  }
  return dom;
};
util.getCss = function(dom, key) {
  return dom.currentStyle ? dom.currentStyle[key] : document.defaultView.getComputedStyle(dom, false)[key];
};
util.padStart = function(str, length, pad) {
  var charstr = String(pad);
  var len = length >> 0;
  var maxlen = Math.ceil(len / charstr.length);
  var chars = [];
  var r = String(str);
  while (maxlen--) {
    chars.push(charstr);
  }
  return chars.join("").substring(0, len - r.length) + r;
};
util.format = function(time) {
  if (window.isNaN(time)) {
    return "";
  }
  time = Math.round(time);
  var hour = util.padStart(Math.floor(time / 3600), 2, 0);
  var minute = util.padStart(Math.floor((time - hour * 3600) / 60), 2, 0);
  var second = util.padStart(Math.floor(time - hour * 3600 - minute * 60), 2, 0);
  return (hour === "00" ? [minute, second] : [hour, minute, second]).join(":");
};
util.event = function(e) {
  if (e.touches) {
    var touch = e.touches[0] || e.changedTouches[0];
    e.clientX = touch.clientX || 0;
    e.clientY = touch.clientY || 0;
    e.offsetX = touch.pageX - touch.target.offsetLeft;
    e.offsetY = touch.pageY - touch.target.offsetTop;
  }
  e._target = e.target || e.srcElement;
};
util.typeOf = function(obj) {
  return Object.prototype.toString.call(obj).match(/([^\s.*]+)(?=]$)/g)[0];
};
util.deepCopy = function(dst, src) {
  if (util.typeOf(src) === "Object" && util.typeOf(dst) === "Object") {
    Object.keys(src).forEach(function(key) {
      if (util.typeOf(src[key]) === "Object" && !(src[key] instanceof Node)) {
        if (dst[key] === void 0 || dst[key] === void 0) {
          dst[key] = src[key];
        } else {
          util.deepCopy(dst[key], src[key]);
        }
      } else if (util.typeOf(src[key]) === "Array") {
        dst[key] = util.typeOf(dst[key]) === "Array" ? dst[key].concat(src[key]) : src[key];
      } else {
        dst[key] = src[key];
      }
    });
    return dst;
  }
};
util.deepMerge = function(dst, src) {
  Object.keys(src).map(function(key) {
    if (util.typeOf(src[key]) === "Array" && util.typeOf(dst[key]) === "Array") {
      if (util.typeOf(dst[key]) === "Array") {
        var _dst$key;
        (_dst$key = dst[key]).push.apply(_dst$key, _toConsumableArray(src[key]));
      }
    } else if (util.typeOf(dst[key]) === util.typeOf(src[key]) && dst[key] !== null && util.typeOf(dst[key]) === "Object" && !(src[key] instanceof window.Node)) {
      util.deepMerge(dst[key], src[key]);
    } else {
      src[key] !== null && (dst[key] = src[key]);
    }
  });
  return dst;
};
util.getBgImage = function(el) {
  var url = (el.currentStyle || window.getComputedStyle(el, null)).backgroundImage;
  if (!url || url === "none") {
    return "";
  }
  var a = document.createElement("a");
  a.href = url.replace(/url\("|"\)/g, "");
  return a.href;
};
util.copyDom = function(dom) {
  if (dom && dom.nodeType === 1) {
    var back = document.createElement(dom.tagName);
    Array.prototype.forEach.call(dom.attributes, function(node) {
      back.setAttribute(node.name, node.value);
    });
    if (dom.innerHTML) {
      back.innerHTML = dom.innerHTML;
    }
    return back;
  } else {
    return "";
  }
};
util.setInterval = function(context, eventName, intervalFunc, frequency) {
  if (!context._interval[eventName]) {
    context._interval[eventName] = window.setInterval(intervalFunc.bind(context), frequency);
  }
};
util.clearInterval = function(context, eventName) {
  clearInterval(context._interval[eventName]);
  context._interval[eventName] = null;
};
util.setTimeout = function(context, fun, time) {
  if (!context._timers) {
    context._timers = [];
  }
  var id = setTimeout(function() {
    fun();
    util.clearTimeout(context, id);
  }, time);
  context._timers.push(id);
  return id;
};
util.clearTimeout = function(context, id) {
  var _timers = context._timers;
  if (util.typeOf(_timers) === "Array") {
    for (var i = 0; i < _timers.length; i++) {
      if (_timers[i] === id) {
        _timers.splice(i, 1);
        clearTimeout(id);
        break;
      }
    }
  } else {
    clearTimeout(id);
  }
};
util.clearAllTimers = function(context) {
  var _timers = context._timers;
  if (util.typeOf(_timers) === "Array") {
    _timers.map(function(item) {
      clearTimeout(item);
    });
    context._timerIds = [];
  }
};
util.createImgBtn = function(name, imgUrl, width, height) {
  var btn = util.createDom("xg-".concat(name), "", {}, "xgplayer-".concat(name, "-img"));
  btn.style.backgroundImage = 'url("'.concat(imgUrl, '")');
  if (width && height) {
    var w, h, unit;
    ["px", "rem", "em", "pt", "dp", "vw", "vh", "vm", "%"].every(function(item) {
      if (width.indexOf(item) > -1 && height.indexOf(item) > -1) {
        w = parseFloat(width.slice(0, width.indexOf(item)).trim());
        h = parseFloat(height.slice(0, height.indexOf(item)).trim());
        unit = item;
        return false;
      } else {
        return true;
      }
    });
    btn.style.width = "".concat(w).concat(unit);
    btn.style.height = "".concat(h).concat(unit);
    btn.style.backgroundSize = "".concat(w).concat(unit, " ").concat(h).concat(unit);
    if (name === "start") {
      btn.style.margin = "-".concat(h / 2).concat(unit, " auto auto -").concat(w / 2).concat(unit);
    } else {
      btn.style.margin = "auto 5px auto 5px";
    }
  }
  return btn;
};
util.Hex2RGBA = function(hex, alpha) {
  var rgb = [];
  if (/^\#[0-9A-F]{3}$/i.test(hex)) {
    var sixHex = "#";
    hex.replace(/[0-9A-F]/ig, function(kw) {
      sixHex += kw + kw;
    });
    hex = sixHex;
  }
  if (/^#[0-9A-F]{6}$/i.test(hex)) {
    hex.replace(/[0-9A-F]{2}/ig, function(kw) {
      rgb.push(parseInt(kw, 16));
    });
    return "rgba(".concat(rgb.join(","), ", ").concat(alpha, ")");
  } else {
    return "rgba(255, 255, 255, 0.1)";
  }
};
util.getFullScreenEl = function() {
  return document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement;
};
util.checkIsFunction = function(fun) {
  return fun && typeof fun === "function";
};
util.checkIsObject = function(obj) {
  return obj !== null && _typeof(obj) === "object";
};
util.hide = function(dom) {
  dom.style.display = "none";
};
util.show = function(dom, display) {
  dom.style.display = display || "block";
};
util.isUndefined = function(val) {
  if (typeof val === "undefined" || val === null) {
    return true;
  }
};
util.isNotNull = function(val) {
  return !(val === void 0 || val === null);
};
util.setStyleFromCsstext = function(dom, text) {
  if (!text) {
    return;
  }
  if (util.typeOf(text) === "String") {
    var styleArr = text.replace(/\s+/g, "").split(";");
    styleArr.map(function(item) {
      if (item) {
        var arr = item.split(":");
        if (arr.length > 1) {
          dom.style[arr[0]] = arr[1];
        }
      }
    });
  } else {
    Object.keys(text).map(function(key) {
      dom.style[key] = text[key];
    });
  }
};
function checkIsIn(key, list) {
  for (var i = 0, len = list.length; i < len; i++) {
    if (key.indexOf(list[i]) > -1) {
      return true;
    }
  }
  return false;
}
util.filterStyleFromText = function(dom) {
  var list = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : ["width", "height", "top", "left", "bottom", "right", "position", "z-index", "padding", "margin", "transform"];
  var _cssText = dom.style.cssText;
  if (!_cssText) {
    return {};
  }
  var styleArr = _cssText.replace(/\s+/g, "").split(";");
  var ret = {};
  var remain = {};
  styleArr.map(function(item) {
    if (item) {
      var arr = item.split(":");
      if (arr.length > 1) {
        if (checkIsIn(arr[0], list)) {
          ret[arr[0]] = arr[1];
        } else {
          remain[arr[0]] = arr[1];
        }
      }
    }
  });
  dom.setAttribute("style", "");
  Object.keys(remain).map(function(key) {
    dom.style[key] = remain[key];
  });
  return ret;
};
util.getStyleFromCsstext = function(dom) {
  var _cssText = dom.style.cssText;
  if (!_cssText) {
    return {};
  }
  var styleArr = _cssText.replace(/\s+/g, "").split(";");
  var ret = {};
  styleArr.map(function(item) {
    if (item) {
      var arr = item.split(":");
      if (arr.length > 1) {
        ret[arr[0]] = arr[1];
      }
    }
  });
  return ret;
};
util.preloadImg = function(url) {
  var onload = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : function() {
  };
  var onerror = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : function() {
  };
  if (!url) {
    return;
  }
  var img = new window.Image();
  img.onload = function(e) {
    img = null;
    onload && onload(e);
  };
  img.onerror = function(e) {
    img = null;
    onerror && onerror(e);
  };
  img.src = url;
};
util.stopPropagation = function(e) {
  if (e) {
    e.stopPropagation();
  }
};
util.scrollTop = function() {
  return window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
};
util.scrollLeft = function() {
  return window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;
};
util.checkTouchSupport = function() {
  return "ontouchstart" in window;
};
util.getBuffered2 = function(vbuffered) {
  var maxHoleDuration = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 0.5;
  var buffered = [];
  for (var i = 0; i < vbuffered.length; i++) {
    buffered.push({
      start: vbuffered.start(i) < 0.5 ? 0 : vbuffered.start(i),
      end: vbuffered.end(i)
    });
  }
  buffered.sort(function(a, b) {
    var diff = a.start - b.start;
    if (diff) {
      return diff;
    } else {
      return b.end - a.end;
    }
  });
  var buffered2 = [];
  if (maxHoleDuration) {
    for (var _i = 0; _i < buffered.length; _i++) {
      var buf2len = buffered2.length;
      if (buf2len) {
        var buf2end = buffered2[buf2len - 1].end;
        if (buffered[_i].start - buf2end < maxHoleDuration) {
          if (buffered[_i].end > buf2end) {
            buffered2[buf2len - 1].end = buffered[_i].end;
          }
        } else {
          buffered2.push(buffered[_i]);
        }
      } else {
        buffered2.push(buffered[_i]);
      }
    }
  } else {
    buffered2 = buffered;
  }
  return new XgplayerTimeRange(buffered2);
};
util.getEventPos = function(e) {
  var zoom = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 1;
  if (e.touches && e.touches.length > 0) {
    e = e.touches[0];
  }
  return {
    x: e.x / zoom,
    y: e.y / zoom,
    clientX: e.clientX / zoom,
    clientY: e.clientY / zoom,
    offsetX: e.offsetX / zoom,
    offsetY: e.offsetY / zoom,
    pageX: e.pageX / zoom,
    pageY: e.pageY / zoom
  };
};
util.requestAnimationFrame = function(callback) {
  var _fun = window.requestAnimationFrame || window.webkitRequestAnimationFrame || window.mozRequestAnimationFrame || window.oRequestAnimationFrame || window.msRequestAnimationFrame;
  if (_fun) {
    return _fun(callback);
  }
};
util.getHostFromUrl = function(url) {
  if (util.typeOf(url) !== "String") {
    return "";
  }
  var results = url.split("/");
  var domain = "";
  if (results.length > 3 && results[2]) {
    domain = results[2];
  }
  return domain;
};
util.cancelAnimationFrame = function(frameId) {
  var _fun = window.cancelAnimationFrame || window.mozCancelAnimationFrame || window.cancelRequestAnimationFrame;
  _fun && _fun(frameId);
};
util.isMSE = function(video) {
  if (video.media) {
    video = video.media;
  }
  if (!video || !(video instanceof HTMLMediaElement)) {
    return false;
  }
  return /^blob/.test(video.currentSrc) || /^blob/.test(video.src);
};
util.isBlob = function(url) {
  return typeof url === "string" && /^blob/.test(url);
};
util.generateSessionId = function() {
  var did = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 0;
  var d = new Date().getTime();
  try {
    did = parseInt(did);
  } catch (e) {
    did = 0;
  }
  d += did;
  if (window.performance && typeof window.performance.now === "function") {
    d += parseInt(window.performance.now());
  }
  var uuid = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c) {
    var r = (d + Math.random() * 16) % 16 | 0;
    d = Math.floor(d / 16);
    return (c === "x" ? r : r & 3 | 8).toString(16);
  });
  return uuid;
};
util.createEvent = function(eventName) {
  var event;
  if (typeof window.Event === "function") {
    event = new Event(eventName);
  } else {
    event = document.createEvent("Event");
    event.initEvent(eventName, true, true);
  }
  return event;
};
util.adjustTimeByDuration = function(time, duration, isEnded) {
  if (!duration || !time) {
    return time;
  }
  if (time > duration || isEnded && time < duration) {
    return duration;
  }
  return time;
};
util.createPositionBar = function(className, root) {
  var dom = util.createDom("xg-bar", "", {
    "data-index": -1
  }, className);
  root.appendChild(dom);
  return dom;
};
util.getTransformStyle = function() {
  var pos = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
    x: 0,
    y: 0,
    scale: 1,
    rotate: 0
  };
  var transformValue = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : "";
  var styles = {
    scale: "".concat(pos.scale || 1),
    translate: "".concat(pos.x || 0, "%, ").concat(pos.y || 0, "%"),
    rotate: "".concat(pos.rotate || 0, "deg")
  };
  var stylesKeys = Object.keys(styles);
  stylesKeys.forEach(function(key) {
    var reg = new RegExp("".concat(key, "\\([^\\(]+\\)"), "g");
    var fn = "".concat(key, "(").concat(styles[key], ")");
    if (reg.test(transformValue)) {
      reg.lastIndex = -1;
      transformValue = transformValue.replace(reg, fn);
    } else {
      transformValue += "".concat(fn, " ");
    }
  });
  return transformValue;
};
util.convertDeg = function(val) {
  if (Math.abs(val) <= 1) {
    return val * 360;
  }
  return val % 360;
};
util.getIndexByTime = function(time, segments) {
  var _len = segments.length;
  var _index = -1;
  if (_len < 1) {
    return _index;
  }
  if (time <= segments[0].end || _len < 2) {
    _index = 0;
  } else if (time > segments[_len - 1].end) {
    _index = _len - 1;
  } else {
    for (var i = 1; i < _len; i++) {
      if (time > segments[i - 1].end && time <= segments[i].end) {
        _index = i;
        break;
      }
    }
  }
  return _index;
};
util.getOffsetCurrentTime = function(currentTime, segments) {
  var index = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : -1;
  var _index = -1;
  if (index >= 0 && index < segments.length) {
    _index = index;
  } else {
    _index = util.getIndexByTime(currentTime, segments);
  }
  if (_index < 0) {
    return -1;
  }
  var _len = segments.length;
  var _segments$_index = segments[_index], start = _segments$_index.start, end = _segments$_index.end, cTime = _segments$_index.cTime, offset = _segments$_index.offset;
  if (currentTime < start) {
    return cTime;
  } else if (currentTime >= start && currentTime <= end) {
    return currentTime - offset;
  } else if (currentTime > end && _index >= _len - 1) {
    return end;
  }
  return -1;
};
util.getCurrentTimeByOffset = function(offsetTime, segments) {
  var _index = -1;
  if (!segments || segments.length < 0) {
    return offsetTime;
  }
  for (var i = 0; i < segments.length; i++) {
    if (offsetTime <= segments[i].duration) {
      _index = i;
      break;
    }
  }
  if (_index !== -1) {
    var start = segments[_index].start;
    if (_index - 1 < 0) {
      return start + offsetTime;
    } else {
      return start + (offsetTime - segments[_index - 1].duration);
    }
  }
  return offsetTime;
};
function isObject(value) {
  var type = _typeof(value);
  return value !== null && (type === "object" || type === "function");
}
function debounce(func, wait, options) {
  var lastArgs, lastThis, maxWait, result, timerId, lastCallTime;
  var lastInvokeTime = 0;
  var leading = false;
  var maxing = false;
  var trailing = true;
  var useRAF = !wait && wait !== 0 && typeof window.requestAnimationFrame === "function";
  if (typeof func !== "function") {
    throw new TypeError("Expected a function");
  }
  wait = +wait || 0;
  if (isObject(options)) {
    leading = !!options.leading;
    maxing = "maxWait" in options;
    maxWait = maxing ? Math.max(+options.maxWait || 0, wait) : maxWait;
    trailing = "trailing" in options ? !!options.trailing : trailing;
  }
  function invokeFunc(time) {
    var args = lastArgs;
    var thisArg = lastThis;
    lastArgs = lastThis = void 0;
    lastInvokeTime = time;
    result = func.apply(thisArg, args);
    return result;
  }
  function startTimer(pendingFunc, wait2) {
    if (useRAF) {
      window.cancelAnimationFrame(timerId);
      return window.requestAnimationFrame(pendingFunc);
    }
    return setTimeout(pendingFunc, wait2);
  }
  function cancelTimer(id) {
    if (useRAF) {
      return window.cancelAnimationFrame(id);
    }
    clearTimeout(id);
  }
  function leadingEdge(time) {
    lastInvokeTime = time;
    timerId = startTimer(timerExpired, wait);
    return leading ? invokeFunc(time) : result;
  }
  function remainingWait(time) {
    var timeSinceLastCall = time - lastCallTime;
    var timeSinceLastInvoke = time - lastInvokeTime;
    var timeWaiting = wait - timeSinceLastCall;
    return maxing ? Math.min(timeWaiting, maxWait - timeSinceLastInvoke) : timeWaiting;
  }
  function shouldInvoke(time) {
    var timeSinceLastCall = time - lastCallTime;
    var timeSinceLastInvoke = time - lastInvokeTime;
    return lastCallTime === void 0 || timeSinceLastCall >= wait || timeSinceLastCall < 0 || maxing && timeSinceLastInvoke >= maxWait;
  }
  function timerExpired() {
    var time = Date.now();
    if (shouldInvoke(time)) {
      return trailingEdge(time);
    }
    timerId = startTimer(timerExpired, remainingWait(time));
  }
  function trailingEdge(time) {
    timerId = void 0;
    if (trailing && lastArgs) {
      return invokeFunc(time);
    }
    lastArgs = lastThis = void 0;
    return result;
  }
  function cancel() {
    if (timerId !== void 0) {
      cancelTimer(timerId);
    }
    lastInvokeTime = 0;
    lastArgs = lastCallTime = lastThis = timerId = void 0;
  }
  function flush() {
    return timerId === void 0 ? result : trailingEdge(Date.now());
  }
  function pending() {
    return timerId !== void 0;
  }
  function debounced() {
    var time = Date.now();
    var isInvoking = shouldInvoke(time);
    for (var _len2 = arguments.length, args = new Array(_len2), _key = 0; _key < _len2; _key++) {
      args[_key] = arguments[_key];
    }
    lastArgs = args;
    lastThis = this;
    lastCallTime = time;
    if (isInvoking) {
      if (timerId === void 0) {
        return leadingEdge(lastCallTime);
      }
      if (maxing) {
        timerId = startTimer(timerExpired, wait);
        return invokeFunc(lastCallTime);
      }
    }
    if (timerId === void 0) {
      timerId = startTimer(timerExpired, wait);
    }
    return result;
  }
  debounced.cancel = cancel;
  debounced.flush = flush;
  debounced.pending = pending;
  return debounced;
}
function throttle(func, wait, options) {
  var leading = true;
  var trailing = true;
  if (typeof func !== "function") {
    throw new TypeError("Expected a function");
  }
  if (isObject(options)) {
    leading = "leading" in options ? !!options.leading : leading;
    trailing = "trailing" in options ? !!options.trailing : trailing;
  }
  return debounce(func, wait, {
    leading,
    trailing,
    maxWait: wait
  });
}
function getLang() {
  var lang = (document.documentElement.getAttribute("lang") || navigator.language || "zh-cn").toLocaleLowerCase();
  if (lang === "zh-cn") {
    lang = "zh";
  }
  return lang;
}
function checkIsCurrentVideo(element, playerId, key) {
  if (!element) {
    return;
  }
  var pid = element.getAttribute(key);
  if (pid && pid === playerId && (element.tagName === "VIDEO" || element.tagName === "AUDIO")) {
    return true;
  }
  return false;
}
export { checkIsCurrentVideo, debounce, util as default, getLang, throttle };
