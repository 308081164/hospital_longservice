import { createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var OptionList = /* @__PURE__ */ function() {
  function OptionList2(args) {
    _classCallCheck(this, OptionList2);
    this.config = args.config;
    this.parent = args.root;
    this.root = util.createDom("ul", "", {}, "xg-options-list xg-list-slide-scroll ".concat(this.config.className));
    args.root.appendChild(this.root);
    var maxHeight = this.config.maxHeight;
    if (maxHeight) {
      this.setStyle({
        maxHeight
      });
    }
    this.onItemClick = this.onItemClick.bind(this);
    this.renderItemList();
    var eventName = this.config.domEventType === "touch" ? "touchend" : "click";
    this._delegates = Plugin.delegate.call(this, this.root, "li", eventName, this.onItemClick);
  }
  _createClass(OptionList2, [{
    key: "renderItemList",
    value: function renderItemList(data) {
      var _this = this;
      var config = this.config, root = this.root;
      if (data) {
        config.data = data;
      } else {
        data = config.data;
      }
      if (config.style) {
        Object.keys(config.style).map(function(key) {
          root.style[key] = config[key];
        });
      }
      if (data.length > 0) {
        this.attrKeys = Object.keys(data[0]);
      }
      this.root.innerHTML = "";
      data.map(function(item, index) {
        var className = item.selected ? "option-item selected" : "option-item";
        item["data-index"] = index;
        _this.root.appendChild(util.createDom("li", "<span>".concat(item.showText, "</span>"), item, className));
      });
    }
  }, {
    key: "onItemClick",
    value: function onItemClick(e) {
      if (!e.delegateTarget) {
        e.delegateTarget = e.target;
      }
      var target = e.delegateTarget;
      if (target && util.hasClass(target, "selected")) {
        return false;
      }
      var changeCallback = typeof this.config.onItemClick === "function" ? this.config.onItemClick : null;
      var curSelected = this.root.querySelector(".selected");
      util.addClass(target, "selected");
      curSelected && util.removeClass(curSelected, "selected");
      changeCallback(e, {
        from: curSelected ? this.getAttrObj(curSelected, this.attrKeys) : null,
        to: this.getAttrObj(target, this.attrKeys)
      });
    }
  }, {
    key: "getAttrObj",
    value: function getAttrObj(dom, keys) {
      if (!dom || !keys) {
        return {};
      }
      var obj = {};
      keys.map(function(key) {
        obj[key] = dom.getAttribute(key);
      });
      var index = dom.getAttribute("data-index");
      if (index) {
        obj.index = Number(index);
      }
      return obj;
    }
  }, {
    key: "show",
    value: function show() {
      util.removeClass(this.root, "hide");
      util.addClass(this.root, "active");
    }
  }, {
    key: "hide",
    value: function hide() {
      util.removeClass(this.root, "active");
      util.addClass(this.root, "hide");
    }
  }, {
    key: "setStyle",
    value: function setStyle(style) {
      var _this2 = this;
      Object.keys(style).forEach(function(key) {
        _this2.root.style[key] = style[key];
      });
    }
  }, {
    key: "destroy",
    value: function destroy() {
      if (this._delegates) {
        this._delegates.map(function(item) {
          item.destroy && item.destroy();
        });
        this._delegates = null;
      }
      this.root.innerHTML = null;
      this.parent.removeChild(this.root);
      this.root = null;
    }
  }]);
  return OptionList2;
}();
export { OptionList as default };
