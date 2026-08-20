export type Player = import('./player').default;
export type IError = {
    [propName: string]: any;
    playerVersion: string;
    currentTime: number;
    duration: number;
    ended: boolean;
    readyState: number;
    networkState: number;
    src: any;
    errorType: string;
    errorCode: number;
    message: string;
    mediaError?: {
        code: number;
        message?: string;
    };
    originError?: any;
    url?: any;
    host?: string;
};
/**
 * @typedef { {
 *   playerVersion: string,
 *   currentTime: number,
 *   duration: number,
 *   ended: boolean,
 *   readyState: number,
 *   networkState: number,
 *   src: any,
 *   errorType: string,
 *   errorCode: number,
 *   message: string,
 *   mediaError?: {
 *     code: number,
 *     message?: string
 *   },
 *   originError?: any,
 *   url?: any,
 *   host?: string,
 *   [propName: string]: any
 * } } IError
 */
/**
 * @type { IError }
 */
declare class Errors {
    /**
     *
     * @param { Player } player
     * @param { {
     * errorType: string,
     * errorCode: number,
     * errorMessage: string,
     * originError: any,
     * ext: { [propName: string]: any; }
     * } } errorInfo
     * @returns
     */
    constructor(player: Player, errorInfo?: {
        errorType: string;
        errorCode: number;
        errorMessage: string;
        originError: any;
        ext: {
            [propName: string]: any;
        };
    }, ...args: any[]);
}
export namespace ErrorTypes {
    namespace network {
        const code: number;
    }
    namespace mse {
        const code_1: number;
        export { code_1 as code };
    }
    namespace parse {
        const code_2: number;
        export { code_2 as code };
    }
    namespace format {
        const code_3: number;
        export { code_3 as code };
    }
    namespace decoder {
        const code_4: number;
        export { code_4 as code };
    }
    namespace runtime {
        const code_5: number;
        export { code_5 as code };
    }
    namespace timeout {
        const code_6: number;
        export { code_6 as code };
    }
    namespace drm {
        const code_7: number;
        export { code_7 as code };
    }
    namespace ad {
        const code_8: number;
        export { code_8 as code };
    }
    namespace other {
        const code_9: number;
        export { code_9 as code };
    }
}
/**
 * @typedef { import ('./player').default } Player
 */
export const ERROR_TYPE_MAP: {
    1: string;
    2: string;
    3: string;
    4: string;
    5: string;
    6: string;
};
export { Errors as default };
