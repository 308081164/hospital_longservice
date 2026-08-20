export default sniffer;
export type ICheckResult = {
    isSupport: boolean;
    mime: string;
};
export type ISniffer = {
    device: 'pc' | 'mobile';
    browser: 'ie' | 'firefox' | 'chrome' | 'opera' | 'safari';
    os: {
        isTablet: boolean;
        isPhone: boolean;
        isIpad: boolean;
        isIos: boolean;
        isAndroid: boolean;
        isPc: boolean;
        isSymbian: boolean;
        isWindowsPhone: boolean;
        isFireFox: boolean;
    };
    isWeixin: boolean;
    isSupportMP4(): ICheckResult;
    isHevcSupported(): boolean;
    probeConfigSupported(info: MediaDecodingConfiguration): Promise<MediaCapabilitiesDecodingInfo>;
};
/**
 * @type ISniffer
 */
declare const sniffer: ISniffer;
