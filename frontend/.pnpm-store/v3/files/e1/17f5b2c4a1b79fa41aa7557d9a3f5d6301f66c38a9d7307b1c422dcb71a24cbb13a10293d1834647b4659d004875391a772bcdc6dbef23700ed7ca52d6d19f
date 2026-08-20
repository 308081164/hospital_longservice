export default pluginsManager;
declare namespace pluginsManager {
    const pluginGroup: {};
    function init(player: any): void;
    function init(player: any): void;
    function formatPluginInfo(plugin: any, config: any): {
        PLUFGIN: any;
        options: any;
    };
    function formatPluginInfo(plugin: any, config: any): {
        PLUFGIN: any;
        options: any;
    };
    /**
     * check the plugin if exits in plugins
     * @param {string} pluginName
     * @param { Array <any> } plugins
     * @returns boolean
     */
    function checkPluginIfExits(pluginName: string, plugins: any[]): boolean;
    /**
     * check the plugin if exits in plugins
     * @param {string} pluginName
     * @param { Array <any> } plugins
     * @returns boolean
     */
    function checkPluginIfExits(pluginName: string, plugins: any[]): boolean;
    /**
     * get plugin Config from playerConfig
     * @param { string } pluginName
     * @param { {[propName: string]: any;} } playerConfig
     * @return { {[propName: string]: any;} } pluginConfig
     */
    function getRootByConfig(pluginName: string, playerConfig: {
        [propName: string]: any;
    }): {
        [propName: string]: any;
    };
    /**
     * get plugin Config from playerConfig
     * @param { string } pluginName
     * @param { {[propName: string]: any;} } playerConfig
     * @return { {[propName: string]: any;} } pluginConfig
     */
    function getRootByConfig(pluginName: string, playerConfig: {
        [propName: string]: any;
    }): {
        [propName: string]: any;
    };
    /**
     * register a lazy plugin
     * @param { any } player instance
     * @param { any } lazyPlugin config
     *
     */
    function lazyRegister(player: any, lazyPlugin: any): Promise<any>;
    /**
     * register a lazy plugin
     * @param { any } player instance
     * @param { any } lazyPlugin config
     *
     */
    function lazyRegister(player: any, lazyPlugin: any): Promise<any>;
    /**
    * register a Plugin
    * @param { any } player the plugins register
    * @param { any } plugin the plugin contructor
    * @param { any } options the plugin configuration
    * @return { any } Plugin the plugin instance
    **/
    function register(player: any, plugin: any, options?: any): any;
    /**
    * register a Plugin
    * @param { any } player the plugins register
    * @param { any } plugin the plugin contructor
    * @param { any } options the plugin configuration
    * @return { any } Plugin the plugin instance
    **/
    function register(player: any, plugin: any, options?: any): any;
    /**
     * Unregister a plugin from player instance
     * @param { string } cgid
     * @param { string } name
     */
    function unRegister(cgid: string, name: string): void;
    /**
     * Unregister a plugin from player instance
     * @param { string } cgid
     * @param { string } name
     */
    function unRegister(cgid: string, name: string): void;
    /**
     * remove a plugin instance from the player plugin list
     * @param { any } player
     * @param { string } name
     */
    function deletePlugin(player: any, name: string): void;
    /**
     * remove a plugin instance from the player plugin list
     * @param { any } player
     * @param { string } name
     */
    function deletePlugin(player: any, name: string): void;
    /**
     * get all plugin instance of player
     * @param { any } player
     */
    function getPlugins(player: any): any;
    /**
     * get all plugin instance of player
     * @param { any } player
     */
    function getPlugins(player: any): any;
    function findPlugin(player: any, name: any): any;
    function findPlugin(player: any, name: any): any;
    function beforeInit(player: any): Promise<any>;
    function beforeInit(player: any): Promise<any>;
    function afterInit(player: any): void;
    function afterInit(player: any): void;
    function setLang(lang: any, player: any): void;
    function setLang(lang: any, player: any): void;
    function reRender(player: any): void;
    function reRender(player: any): void;
    function onPluginsReady(player: any): void;
    function onPluginsReady(player: any): void;
    function destroy(player: any): void;
    function destroy(player: any): void;
}
