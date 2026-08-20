import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, get as _get, getPrototypeOf as _getPrototypeOf, typeof as _typeof } from "../_virtual/_rollupPluginBabelHelpers.js";
import BasePlugin from "./basePlugin.js";
import _delegate from "delegate";
import XG_DEBUG from "../utils/debug.js";
import util from "../utils/util.js";
var ROOT_TYPES = {
  CONTROLS: "controls",
  ROOT: "root"
};
var POSITIONS = {
  ROOT: "root",
  ROOT_LEFT: "rootLeft",
  ROOT_RIGHT: "rootRight",
  ROOT_TOP: "rootTop",
  CONTROLS_LEFT: "controlsLeft",
  CONTROLS_RIGTH: "controlsRight",
  CONTROLS_RIGHT: "controlsRight",
  CONTROLS_CENTER: "controlsCenter",
  CONTROLS: "controls"
};
var PLUGIN_STATE_CLASS = {
  ICON_DISABLE: "xg-icon-disable",
  ICON_HIDE: "xg-icon-hide"
};
function isUrl(str) {
  if (!str) {
    return false;
  }
  return str.indexOf && /^(?:http|data:|\/)/.test(str);
}
function mergeIconClass(icon, classname) {
  if (_typeof(icon) === "object" && icon.class && typeof icon.class === "string") {
    return "".concat(classname, " ").concat(icon.class);
  }
  return classname;
}
function mergeIconAttr(icon, attr) {
  if (_typeof(icon) === "object" && icon.attr && _typeof(icon.attr) === "object") {
    Object.keys(icon.attr).map(function(key) {
      attr[key] = icon.attr[key];
    });
  }
  return attr;
}
function createIcon(icon, key) {
  var classname = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : "";
  var attr = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : {};
  var pluginName = arguments.length > 4 && arguments[4] !== void 0 ? arguments[4] : "";
  var newIcon = null;
  if (icon instanceof window.Element) {
    util.addClass(icon, classname);
    Object.keys(attr).map(function(key2) {
      icon.setAttribute(key2, attr[key2]);
    });
    return icon;
  }
  if (isUrl(icon) || isUrl(icon.url)) {
    attr.src = isUrl(icon) ? icon : icon.url || "";
    newIcon = util.createDom(icon.tag || "img", "", attr, "xg-img ".concat(classname));
    return newIcon;
  }
  if (typeof icon === "function") {
    try {
      newIcon = icon();
      if (newIcon instanceof window.Element) {
        util.addClass(newIcon, classname);
        Object.keys(attr).map(function(key2) {
          newIcon.setAttribute(key2, attr[key2]);
        });
        return newIcon;
      } else {
        XG_DEBUG.logWarn("warn>>icons.".concat(key, " in config of plugin named [").concat(pluginName, "] is a function mast return an Element Object"));
      }
      return null;
    } catch (e) {
      XG_DEBUG.logError("Plugin named [".concat(pluginName, "]:createIcon"), e);
      return null;
    }
  }
  if (typeof icon === "string") {
    return util.createDomFromHtml(icon, attr, classname);
  }
  XG_DEBUG.logWarn("warn>>icons.".concat(key, " in config of plugin named [").concat(pluginName, "] is invalid"));
  return null;
}
function registerIconsObj(iconsConfig, plugin) {
  var _icons = plugin.config.icons || plugin.playerConfig.icons;
  Object.keys(iconsConfig).map(function(key) {
    var orgIcon = iconsConfig[key];
    var classname = orgIcon && orgIcon.class ? orgIcon.class : "";
    var attr = orgIcon && orgIcon.attr ? orgIcon.attr : {};
    var newIcon = null;
    if (_icons && _icons[key]) {
      classname = mergeIconClass(_icons[key], classname);
      attr = mergeIconAttr(_icons[key], attr);
      newIcon = createIcon(_icons[key], key, classname, attr, plugin.pluginName);
    }
    if (!newIcon && orgIcon) {
      newIcon = createIcon(orgIcon.icon ? orgIcon.icon : orgIcon, attr, classname, {}, plugin.pluginName);
    }
    plugin.icons[key] = newIcon;
  });
}
function registerTextObj(textConfig, plugin) {
  Object.keys(textConfig).map(function(key) {
    Object.defineProperty(plugin.langText, key, {
      get: function get() {
        var lang = plugin.lang, i18n = plugin.i18n;
        if (i18n[key]) {
          return i18n[key];
        } else {
          return textConfig[key] ? textConfig[key][lang] || "" : "";
        }
      }
    });
  });
}
var Plugin = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(Plugin2, _BasePlugin);
  var _super = _createSuper(Plugin2);
  function Plugin2() {
    var _this;
    var args = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
    _classCallCheck(this, Plugin2);
    _this = _super.call(this, args);
    _this.__delegates = [];
    return _this;
  }
  _createClass(Plugin2, [{
    key: "__init",
    value: function __init(args) {
      _get(_getPrototypeOf(Plugin2.prototype), "__init", this).call(this, args);
      if (!args.root) {
        return;
      }
      var _parent = args.root;
      var _el = null;
      this.icons = {};
      this.root = null;
      this.parent = null;
      this.extraEls = [];
      var _orgicons = this.registerIcons() || {};
      registerIconsObj(_orgicons, this);
      this.langText = {};
      var defaultTexConfig = this.registerLanguageTexts() || {};
      registerTextObj(defaultTexConfig, this);
      var renderStr = "";
      try {
        renderStr = this.render();
      } catch (e) {
        XG_DEBUG.logError("Plugin:".concat(this.pluginName, ":render"), e);
        throw new Error("Plugin:".concat(this.pluginName, ":render:").concat(e.message));
      }
      if (renderStr) {
        _el = Plugin2.insert(renderStr, _parent, args.index);
        _el.setAttribute("data-index", args.index);
      } else if (args.tag) {
        _el = util.createDom(args.tag, "", args.attr, args.name);
        _el.setAttribute("data-index", args.index);
        _parent.appendChild(_el);
      } else {
        return;
      }
      this.root = _el;
      this.parent = _parent;
      var attr = this.config.attr || {};
      var style = this.config.style || {};
      this.setAttr(attr);
      this.setStyle(style);
      if (this.config.index) {
        this.root.setAttribute("data-index", this.config.index);
      }
      this.__registerChildren();
    }
  }, {
    key: "__registerChildren",
    value: function __registerChildren() {
      var _this2 = this;
      if (!this.root) {
        return;
      }
      this._children = [];
      var children = this.children();
      if (children && _typeof(children) === "object") {
        if (Object.keys(children).length > 0) {
          Object.keys(children).map(function(item) {
            var name = item;
            var _plugin = children[name];
            var options = {
              root: _this2.root
            };
            var config, _Plugin;
            if (typeof _plugin === "function") {
              config = _this2.config[name] || {};
              _Plugin = _plugin;
            } else if (_typeof(_plugin) === "object" && typeof _plugin.plugin === "function") {
              config = _plugin.options ? util.deepCopy(_this2.config[name] || {}, _plugin.options) : _this2.config[name] || {};
              _Plugin = _plugin.plugin;
            }
            options.config = config;
            config.index !== void 0 && (options.index = config.index);
            config.root && (options.root = config.root);
            _this2.registerPlugin(_Plugin, options, name);
          });
        }
      }
    }
  }, {
    key: "updateLang",
    value: function updateLang(lang) {
      if (!lang) {
        lang = this.lang;
      }
      function checkChildren(node, callback) {
        for (var i = 0; i < node.children.length; i++) {
          if (node.children[i].children.length > 0) {
            checkChildren(node.children[i], callback);
          } else {
            callback(node.children[i]);
          }
        }
      }
      var root = this.root, i18n = this.i18n, langText = this.langText;
      if (root) {
        checkChildren(root, function(node) {
          var langKey = node.getAttribute && node.getAttribute("lang-key");
          if (!langKey) {
            return;
          }
          var langTextShow = i18n[langKey.toUpperCase()] || langText[langKey];
          if (langTextShow) {
            node.innerHTML = typeof langTextShow === "function" ? langTextShow(lang) : langTextShow;
          }
        });
      }
    }
  }, {
    key: "lang",
    get: function get() {
      return this.player.lang;
    }
  }, {
    key: "changeLangTextKey",
    value: function changeLangTextKey(dom) {
      var key = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : "";
      var i18n = this.i18n || {};
      var langText = this.langText;
      dom.setAttribute && dom.setAttribute("lang-key", key);
      var text = i18n[key] || langText[key] || "";
      if (text) {
        dom.innerHTML = text;
      }
    }
  }, {
    key: "plugins",
    value: function plugins() {
      return this._children;
    }
  }, {
    key: "disable",
    value: function disable() {
      this.config.disable = true;
      util.addClass(this.find(".xgplayer-icon"), PLUGIN_STATE_CLASS.ICON_DISABLE);
    }
  }, {
    key: "enable",
    value: function enable() {
      this.config.disable = false;
      util.removeClass(this.find(".xgplayer-icon"), PLUGIN_STATE_CLASS.ICON_DISABLE);
    }
  }, {
    key: "children",
    value: function children() {
      return {};
    }
  }, {
    key: "registerPlugin",
    value: function registerPlugin(plugin) {
      var options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      var name = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : "";
      options.root = options.root || this.root;
      var _c = _get(_getPrototypeOf(Plugin2.prototype), "registerPlugin", this).call(this, plugin, options, name);
      this._children.push(_c);
      return _c;
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {};
    }
  }, {
    key: "registerLanguageTexts",
    value: function registerLanguageTexts() {
      return {};
    }
  }, {
    key: "find",
    value: function find(qs) {
      if (!this.root) {
        return;
      }
      return this.root.querySelector(qs);
    }
  }, {
    key: "bind",
    value: function bind(querySelector, eventType, callback) {
      var _this3 = this;
      if (arguments.length < 3 && typeof eventType === "function") {
        if (Array.isArray(querySelector)) {
          querySelector.forEach(function(item) {
            _this3.bindEL(item, eventType);
          });
        } else {
          this.bindEL(querySelector, eventType);
        }
      } else {
        var ret = Plugin2.delegate.call(this, this.root, querySelector, eventType, callback);
        this.__delegates = this.__delegates.concat(ret);
      }
    }
  }, {
    key: "unbind",
    value: function unbind(querySelector, eventType) {
      var _this4 = this;
      if (arguments.length < 3 && typeof eventType === "function") {
        if (Array.isArray(querySelector)) {
          querySelector.forEach(function(item) {
            _this4.unbindEL(item, eventType);
          });
        } else {
          this.unbindEL(querySelector, eventType);
        }
      } else {
        var key = "".concat(querySelector, "_").concat(eventType);
        for (var i = 0; i < this.__delegates.length; i++) {
          if (this.__delegates[i].key === key) {
            this.__delegates[i].destroy();
            this.__delegates.splice(i, 1);
            break;
          }
        }
      }
    }
  }, {
    key: "setStyle",
    value: function setStyle(name, value) {
      var _this5 = this;
      if (!this.root) {
        return;
      }
      if (util.typeOf(name) === "String") {
        return this.root.style[name] = value;
      } else if (util.typeOf(name) === "Object") {
        Object.keys(name).map(function(key) {
          _this5.root.style[key] = name[key];
        });
      }
    }
  }, {
    key: "setAttr",
    value: function setAttr(name, value) {
      var _this6 = this;
      if (!this.root) {
        return;
      }
      if (util.typeOf(name) === "String") {
        return this.root.setAttribute(name, value);
      } else if (util.typeOf(name) === "Object") {
        Object.keys(name).map(function(key) {
          _this6.root.setAttribute(key, name[key]);
        });
      }
    }
  }, {
    key: "setHtml",
    value: function setHtml(htmlStr, callback) {
      if (!this.root) {
        return;
      }
      this.root.innerHTML = htmlStr;
      if (typeof callback === "function") {
        callback();
      }
    }
  }, {
    key: "bindEL",
    value: function bindEL(event, eventHandle) {
      var isBubble = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : false;
      if (!this.root) {
        return;
      }
      if ("on".concat(event) in this.root && typeof eventHandle === "function") {
        this.root.addEventListener(event, eventHandle, isBubble);
      }
    }
  }, {
    key: "unbindEL",
    value: function unbindEL(event, eventHandle) {
      var isBubble = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : false;
      if (!this.root) {
        return;
      }
      if ("on".concat(event) in this.root && typeof eventHandle === "function") {
        this.root.removeEventListener(event, eventHandle, isBubble);
      }
    }
  }, {
    key: "show",
    value: function show(value) {
      if (!this.root) {
        return;
      }
      this.root.style.display = value !== void 0 ? value : "block";
      var cs = window.getComputedStyle(this.root, null);
      var cssDisplayValue = cs.getPropertyValue("display");
      if (cssDisplayValue === "none") {
        return this.root.style.display = "block";
      }
    }
  }, {
    key: "hide",
    value: function hide() {
      this.root && (this.root.style.display = "none");
    }
  }, {
    key: "appendChild",
    value: function appendChild(pdom, child) {
      if (!this.root) {
        return null;
      }
      if (arguments.length < 2 && arguments[0] instanceof window.Element) {
        return this.root.appendChild(arguments[0]);
      }
      if (!child || !(child instanceof window.Element)) {
        return null;
      }
      try {
        if (typeof pdom === "string") {
          return this.find(pdom).appendChild(child);
        } else {
          return pdom.appendChild(child);
        }
      } catch (err) {
        XG_DEBUG.logError("Plugin:appendChild", err);
        return null;
      }
    }
  }, {
    key: "render",
    value: function render() {
      return "";
    }
  }, {
    key: "destroy",
    value: function destroy() {
    }
  }, {
    key: "__destroy",
    value: function __destroy() {
      var _this7 = this;
      var player = this.player;
      this.__delegates.map(function(item) {
        item.destroy();
      });
      this.__delegates = [];
      if (this._children instanceof Array) {
        this._children.map(function(item) {
          player.unRegisterPlugin(item.pluginName);
        });
        this._children = null;
      }
      if (this.root) {
        if (this.root.hasOwnProperty("remove")) {
          this.root.remove();
        } else if (this.root.parentNode) {
          this.root.parentNode.removeChild(this.root);
        }
      }
      _get(_getPrototypeOf(Plugin2.prototype), "__destroy", this).call(this);
      this.icons = {};
      ["root", "parent"].map(function(item) {
        _this7[item] = null;
      });
      this.extraEls = [];
    }
  }], [{
    key: "insert",
    value: function insert(html, parent) {
      var index = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 0;
      var len = parent.children.length;
      var insertIdx = Number(index);
      var isDomElement = html instanceof window.Node;
      if (len) {
        var i = 0;
        var coordinate = null;
        var mode = "";
        for (; i < len; i++) {
          coordinate = parent.children[i];
          var curIdx = Number(coordinate.getAttribute("data-index"));
          if (curIdx >= insertIdx) {
            mode = "beforebegin";
            break;
          } else if (curIdx < insertIdx) {
            mode = "afterend";
          }
        }
        if (isDomElement) {
          if (mode === "afterend") {
            parent.appendChild(html);
          } else {
            parent.insertBefore(html, coordinate);
          }
        } else {
          coordinate.insertAdjacentHTML(mode, html);
        }
        return mode === "afterend" ? parent.children[parent.children.length - 1] : parent.children[i];
      } else {
        isDomElement ? parent.appendChild(html) : parent.insertAdjacentHTML("beforeend", html);
        return parent.children[parent.children.length - 1];
      }
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {};
    }
  }, {
    key: "delegate",
    value: function delegate(root, querySelector, eventType, callback) {
      var capture = arguments.length > 4 && arguments[4] !== void 0 ? arguments[4] : false;
      var dels = [];
      if (root instanceof window.Node && typeof callback === "function") {
        if (Array.isArray(eventType)) {
          eventType.forEach(function(item) {
            var ret2 = _delegate(root, querySelector, item, callback, capture);
            ret2.key = "".concat(querySelector, "_").concat(item);
            dels.push(ret2);
          });
        } else {
          var ret = _delegate(root, querySelector, eventType, callback, capture);
          ret.key = "".concat(querySelector, "_").concat(eventType);
          dels.push(ret);
        }
      }
      return dels;
    }
  }, {
    key: "ROOT_TYPES",
    get: function get() {
      return ROOT_TYPES;
    }
  }, {
    key: "POSITIONS",
    get: function get() {
      return POSITIONS;
    }
  }]);
  return Plugin2;
}(BasePlugin);
export { PLUGIN_STATE_CLASS, POSITIONS, ROOT_TYPES, Plugin as default };
