package com.vds.brule;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Rule {
  public String serviceId;
  public String viewId;
  public String transactionState;

  //fixed
  public String classification;
}
