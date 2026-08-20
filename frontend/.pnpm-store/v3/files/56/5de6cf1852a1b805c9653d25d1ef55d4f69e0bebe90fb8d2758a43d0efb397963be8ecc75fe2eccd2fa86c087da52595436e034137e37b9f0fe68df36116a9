import { addObserver, unObserver } from "./resizeObserver.js";
import util from "../utils/util.js";
var pluginsManager = {
  pluginGroup: {},
  init: function init(player) {
    var cgid = player._pluginInfoId;
    if (!cgid) {
      cgid = new Date().getTime();
      player._pluginInfoId = cgid;
    }
    !player.config.closeResizeObserver && addObserver(player.root, function() {
      player.resize();
    });
    this.pluginGroup[cgid] = {
      _originalOptions: player.config || {},
      _plugins: {}
    };
  },
  formatPluginInfo: function formatPluginInfo(plugin, config) {
    var PLUFGIN = null;
    var options = null;
    if (plugin.plugin && typeof plugin.plugin === "function") {
      PLUFGIN = plugin.plugin;
      options = plugin.options;
    } else {
      PLUFGIN = plugin;
      options = {};
    }
    if (config) {
      options.config = config || {};
    }
    return {
      PLUFGIN,
      options
    };
  },
  checkPluginIfExits: function checkPluginIfExits(pluginName, plugins) {
    for (var i = 0; i < plugins.length; i++) {
      if (pluginName.toLowerCase() === plugins[i].pluginName.toLowerCase()) {
        return true;
      }
    }
    return false;
  },
  getRootByConfig: function getRootByConfig(pluginName, playerConfig) {
    var keys = Object.keys(playerConfig);
    var _pConfig = null;
    for (var i = 0; i < keys.length; i++) {
      if (pluginName.toLowerCase() === keys[i].toLowerCase()) {
        _pConfig = playerConfig[keys[i]];
        break;
      }
    }
    if (util.typeOf(_pConfig) === "Object") {
      return {
        root: _pConfig.root,
        position: _pConfig.position
      };
    }
    return {};
  },
  lazyRegister: function lazyRegister(player, lazyPlugin) {
    var _this = this;
    var timeout = lazyPlugin.timeout || 1500;
    return Promise.race([lazyPlugin.loader().then(function(plugin) {
      var result;
      if (plugin && plugin.__esModule) {
        result = plugin.default;
      } else {
        result = plugin;
      }
      _this.register(player, result, plugin.options);
    }), new Promise(function(resolve, reject) {
      setTimeout(function() {
        reject(new Error("timeout"));
      }, timeout);
    })]);
  },
  register: function register(player, plugin) {
    var options = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {};
    if (!player || !plugin || typeof plugin !== "function" || plugin.prototype === void 0) {
      return;
    }
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return;
    }
    if (!this.pluginGroup[cgid]._plugins) {
      this.pluginGroup[cgid]._plugins = {};
    }
    var plugins = this.pluginGroup[cgid]._plugins;
    var originalOptions = this.pluginGroup[cgid]._originalOptions;
    options.player = player;
    var pluginName = options.pluginName || plugin.pluginName;
    if (!pluginName) {
      throw new Error("The property pluginName is necessary");
    }
    if (plugin.isSupported && !plugin.isSupported(player.config.mediaType, player.config.codecType)) {
      console.warn("not supported plugin [".concat(pluginName, "]"));
      return;
    }
    if (!options.config) {
      options.config = {};
    }
    var keys = Object.keys(originalOptions);
    for (var i = 0; i < keys.length; i++) {
      if (pluginName.toLowerCase() === keys[i].toLowerCase()) {
        var config = originalOptions[keys[i]];
        if (util.typeOf(config) === "Object") {
          options.config = Object.assign({}, options.config, originalOptions[keys[i]]);
        } else if (util.typeOf(config) === "Boolean") {
          options.config.disable = !config;
        }
        break;
      }
    }
    if (plugin.defaultConfig) {
      Object.keys(plugin.defaultConfig).forEach(function(key) {
        if (typeof options.config[key] === "undefined") {
          options.config[key] = plugin.defaultConfig[key];
        }
      });
    }
    if (!options.root) {
      options.root = player.root;
    } else if (typeof options.root === "string") {
      options.root = player[options.root];
    }
    options.index = options.config.index || 0;
    try {
      if (plugins[pluginName.toLowerCase()]) {
        this.unRegister(cgid, pluginName.toLowerCase());
        console.warn("the is one plugin with same pluginName [".concat(pluginName, "] exist, destroy the old instance"));
      }
      var _instance = new plugin(options);
      plugins[pluginName.toLowerCase()] = _instance;
      plugins[pluginName.toLowerCase()].func = plugin;
      if (_instance && typeof _instance.afterCreate === "function") {
        _instance.afterCreate();
      }
      return _instance;
    } catch (err) {
      console.error(err);
      throw err;
    }
  },
  unRegister: function unRegister(cgid, name) {
    if (cgid._pluginInfoId) {
      cgid = cgid._pluginInfoId;
    }
    name = name.toLowerCase();
    try {
      var plugin = this.pluginGroup[cgid]._plugins[name];
      if (plugin) {
        plugin.pluginName && plugin.__destroy();
        delete this.pluginGroup[cgid]._plugins[name];
      }
    } catch (e) {
      console.error("[unRegister:".concat(name, "] cgid:[").concat(cgid, "] error"), e);
    }
  },
  deletePlugin: function deletePlugin(player, name) {
    var cgid = player._pluginInfoId;
    if (cgid && this.pluginGroup[cgid] && this.pluginGroup[cgid]._plugins) {
      delete this.pluginGroup[cgid]._plugins[name];
    }
  },
  getPlugins: function getPlugins(player) {
    var cgid = player._pluginInfoId;
    return cgid && this.pluginGroup[cgid] ? this.pluginGroup[cgid]._plugins : {};
  },
  findPlugin: function findPlugin(player, name) {
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return null;
    }
    var cName = name.toLowerCase();
    return this.pluginGroup[cgid]._plugins[cName];
  },
  beforeInit: function beforeInit(player) {
    var _this2 = this;
    function retPromise(fun) {
      if (!fun || !fun.then) {
        return new Promise(function(resolve) {
          resolve();
        });
      } else {
        return fun;
      }
    }
    return new Promise(function(resolve) {
      if (!_this2.pluginGroup) {
        return;
      }
      var prevTask;
      if (player._loadingPlugins && player._loadingPlugins.length) {
        prevTask = Promise.all(player._loadingPlugins);
      } else {
        prevTask = Promise.resolve();
      }
      return prevTask.then(function() {
        var cgid = player._pluginInfoId;
        if (!_this2.pluginGroup[cgid]) {
          resolve();
          return;
        }
        var plugins = _this2.pluginGroup[cgid]._plugins;
        var pluginsRet = [];
        Object.keys(plugins).forEach(function(pName) {
          if (plugins[pName] && plugins[pName].beforePlayerInit) {
            try {
              var ret = plugins[pName].beforePlayerInit();
              pluginsRet.push(retPromise(ret));
            } catch (e) {
              pluginsRet.push(retPromise(null));
              throw e;
            }
          }
        });
        Promise.all([].concat(pluginsRet)).then(function() {
          resolve();
        }).catch(function(e) {
          console.error(e);
          resolve();
        });
      });
    });
  },
  afterInit: function afterInit(player) {
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return;
    }
    var plugins = this.pluginGroup[cgid]._plugins;
    Object.keys(plugins).forEach(function(pName) {
      if (plugins[pName] && plugins[pName].afterPlayerInit) {
        plugins[pName].afterPlayerInit();
      }
    });
  },
  setLang: function setLang(lang, player) {
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return;
    }
    var plugins = this.pluginGroup[cgid]._plugins;
    Object.keys(plugins).forEach(function(item) {
      if (plugins[item].updateLang) {
        plugins[item].updateLang(lang);
      } else {
        try {
          plugins[item].lang = lang;
        } catch (error) {
          console.warn("".concat(item, " setLang"));
        }
      }
    });
  },
  reRender: function reRender(player) {
    var _this3 = this;
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return;
    }
    var _pList = [];
    var plugins = this.pluginGroup[cgid]._plugins;
    Object.keys(plugins).forEach(function(pName) {
      if (pName !== "controls" && plugins[pName]) {
        _pList.push({
          plugin: plugins[pName].func,
          options: plugins[pName].__args
        });
        _this3.unRegister(cgid, pName);
      }
    });
    _pList.forEach(function(item) {
      _this3.register(player, item.plugin, item.options);
    });
  },
  onPluginsReady: function onPluginsReady(player) {
    var cgid = player._pluginInfoId;
    if (!cgid || !this.pluginGroup[cgid]) {
      return;
    }
    var plugins = this.pluginGroup[cgid]._plugins || {};
    Object.keys(plugins).forEach(function(key) {
      if (plugins[key].onPluginsReady && typeof plugins[key].onPluginsReady === "function") {
        plugins[key].onPluginsReady();
      }
    });
  },
  destroy: function destroy(player) {
    var cgid = player._pluginInfoId;
    if (!this.pluginGroup[cgid]) {
      return;
    }
    unObserver(player.root);
    var plugins = this.pluginGroup[cgid]._plugins;
    for (var _i = 0, _Object$keys = Object.keys(plugins); _i < _Object$keys.length; _i++) {
      var item = _Object$keys[_i];
      this.unRegister(cgid, item);
    }
    delete this.pluginGroup[cgid];
    delete player._pluginInfoId;
  }
};
export { pluginsManager as default };
