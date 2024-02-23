package com.inmobi.supply.sun.exception;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * sun工程异常
 * checked exception
 *
 * @author remi.li
 */
@Data
@NoArgsConstructor
@ToString
public class SunException extends Exception {
    private TermCode termCode; //错误码
    private RunTimeContext runTimeContext; //上下文信息
    @Deprecated
    private String uncheckException; //runtime exception desc*/
    private Throwable throwable;

    public SunException(TermCode termCode, RunTimeContext runTimeContext) {
        this.termCode = termCode;
        //this.runTimeContext = runTimeContext;
        this.uncheckException = "";
    }

    public SunException(TermCode termCode) {
        this.termCode = termCode;
        //this.runTimeContext = runTimeContext;
        this.uncheckException = "";
    }

    public SunException(TermCode termCode, RunTimeContext runTimeContext, Throwable throwable) {
        this.termCode = termCode;
        //this.runTimeContext = runTimeContext;
        this.throwable = throwable;
    }
}
