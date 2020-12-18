package com.vds.brule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Tree {

  private static final String DONTCAREVAL = "*";
  private final Field[] fields;
  private final List<String> classes;
  private final List<String> attributes;
  private int level;
  private int leafs;
  private int nodes;
  private List<Rule> rules;
  private Map<String, List<String>> attributesValues;
  private Map<String, Integer> usedAttributes;
  private String[] ca1;
  private int[][] ca2;

  public Tree(List<Rule> rules) {
    level = 0;
    leafs = 0;
    nodes = 0;
    this.rules = rules;
    attributesValues = new HashMap<>();
    fields = Rule.class.getDeclaredFields();
    usedAttributes = new HashMap<>();
    ca1 = new String[]{};
    ca2 = new int[][]{};

    for (Rule rule : rules) {
      for (Field field : fields) {
        if (field.getName().equals("classification")) {
          continue;
        }
        try {
          String value = String.valueOf(field.get(rule));
          if (value != null) {
            List<String> attributeValues = attributesValues
                .getOrDefault(field.getName(), new ArrayList<>());
            if (!attributeValues.contains(value) && !value.equals(DONTCAREVAL)) {
              attributeValues.add(value);
              attributesValues.put(field.getName(), attributeValues);
            }
          }
        } catch (IllegalAccessException ignored) {
        }
      }
    }

    for (Map.Entry<String, List<String>> entry : attributesValues.entrySet()) {
      entry.getValue().add(DONTCAREVAL);
    }

    attributes = new ArrayList<>();
    for (Field field : fields) {
      if (field.getName().equals("classification")) {
        continue;
      }
      attributes.add(field.getName());
    }
    classes = new ArrayList<>();
    classes.add("true");
    classes.add("false");
  }

  private Object createRBDT1(List<Rule> rules) {
    String best;
    level++;
    best = attributesMatrix(rules);
    String attributeNew;
    Map<String, Object> tree = new HashMap<>();
    if (classes.contains(best)) {
      // create leaf node
      leafs += 1;
      return best;
    } else {
      tree.put(best, null);
      nodes += 1;
      attributeNew = best;
      usedAttributes.put(attributeNew, level);
      Map<String, Object> add = new HashMap<>();
      for (String val : getValues(best)) {
        Object subTree = createRBDT1(getRules(rules, best, val));
        if (subTree instanceof Map) {
          add.put(val, subTree);
        } else {
          add.put(val, subTree);
        }
        tree.put(best, add);
        level -= 1;
      }
    }
    usedAttributes.remove(attributeNew);
    return tree;
  }

  private List<Rule> getRules(List<Rule> rules, String attribute, String value) {
    List<Rule> rtnRules = new ArrayList<>();
    for (Rule rule : rules) {
      try {
        String attributeVal = rule.getClass().getField(attribute).get(rule).toString();
        if (attributeVal.equals(value) || attributeVal.equals(DONTCAREVAL)) {
          rtnRules.add(rule);
        }
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }

    }
    return rtnRules;
  }

  private List<String> getValues(String attribute) {
    List<String> values = new ArrayList<>();
    for (String a : attributesValues.getOrDefault(attribute, new ArrayList<>())) {
      if (!a.equals(DONTCAREVAL)) {
        values.add(a);
      }
    }
    return values;
  }

  private String attributesMatrix(List<Rule> rules) {
    String best;
    for (String attribute : attributes) { // loop each attribute
      int[][] am = new int[classes.size()][];
      Arrays.fill(am, new int[]{});
      for (int i = 0; i < am.length; i++) { // loop each classification
        List<String> possibleValues = attributesValues
            .get(attribute); // get possible value of an attribute
        int flag = 0;
        for (String possibleValue : possibleValues) {
          for (Rule rule : rules) {
            try {
              if (rule.getClassification().equals(classes.get(i)) && possibleValue
                  .equals(rule.getClass().getField(attribute).get(rule).toString())) {
                flag = 1;
              }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
          }
          am[i] = Arrays.copyOf(am[i], am[i].length + 1);
          if (flag == 1) {
            am[i][am[i].length - 1] = 1;
          } else {
            am[i][am[i].length - 1] = 0;
          }
          flag = 0;
        }
      }
      int[] cvj = new int[]{}; // its size will be number of values of the attribute
      int x1 = 0; // score of single value for all classes, if x1==0 mean it didnt participate in any class
      for (int i = 0; i < am[0].length; i++) {
        for (int[] ints : am) {
          x1 += ints[i];
        }
        cvj = Arrays.copyOf(cvj, cvj.length + 1);
        cvj[cvj.length - 1] = x1;
        x1 = 0;
      }
      int flag = 0;
      for (Map.Entry<String, Integer> entry : usedAttributes.entrySet()) {
        if (entry.getKey().equals(attribute) && entry.getValue() < level) {
          flag = 1;
          break;
        }
      }
      if (flag == 0) {
        ca1 = Arrays.copyOf(ca1, ca1.length + 1);
        ca1[ca1.length - 1] = attribute;
        ca2 = Arrays.copyOf(ca2, ca2.length + 1);
        ca2[ca2.length - 1] = cvj;
      }
    }
    best = call(rules);
    ca1 = new String[]{};
    ca2 = new int[][]{};
    return best;
  }

  private String call(List<Rule> rules) {
    String best;
    List<String> leafNodeValues = new ArrayList<>();
    for (Rule rule : rules) {
      leafNodeValues.add(rule.classification);
    }
    if (rules.isEmpty()) {
      best = getMajorityValue();
    } else if (leafNodeValues.size() == leafNodeValues.stream()
        .filter(leafNodeValue -> leafNodeValue.equals(leafNodeValues.get(0))).count()) {
      best = leafNodeValues.get(0);
    } else {
      best = calcAE(this.ca1, this.ca2);
    }
    return best;
  }

  private String calcAE(String[] ca1, int[][] ca2) {
    String best;

    List<String> attributeWithMaxAE = new ArrayList<>(); // fit attribute
    if (ca1.length > 0) {
      int y = ca2[0][ca2[0].length - 1];

      for (int[] ints : ca2) { // find the smallest don't care value in list of attributes
        if (ints[ints.length - 1] < y) {
          y = ints[ints.length - 1];
        }
      }

      for (int i = 0; i < ca2.length; i++) {
        if (ca2[i][ca2[i].length - 1] == y) {
          attributeWithMaxAE.add(ca1[i]);
        }
      }
    }

    if (attributeWithMaxAE.isEmpty()) {
      best = getMajorityValue();
    } else if (attributeWithMaxAE.size() == 1) {
      best = attributeWithMaxAE.get(0);
    } else {
      best = MVD(attributeWithMaxAE, rules);
    }
    return best;
  }

  private String MVD(List<String> maxDom, List<Rule> rules) {
    Map<String, List<String>> values = getMVDValue(rules);
    List<String> firstAttributeVal = values.entrySet().iterator().next().getValue();
    List<String> mvd = new ArrayList<>();
    int minV = firstAttributeVal.size();
    for (String att : maxDom) {
      if (values.get(att).size() < minV) {
        minV = values.get(att).size();
      }
    }
    for (String att : maxDom) {
      if (values.get(att).size() == minV) {
        mvd.add(att);
      }
    }
    Random random = new Random();
    return mvd.get(random.nextInt(mvd.size()));
  }

  private Map<String, List<String>> getMVDValue(List<Rule> rules) {
    Map<String, List<String>> values = new HashMap<>();
    List<String> temp = new ArrayList<>();
    for (int i = 0; i < fields.length - 1; i++) {
      if (fields[i].getName().equals("classification")) {
        continue;
      }
      for (Rule rule : rules) {
        try {
          String attributeValue = rule.getClass().getField(fields[i].getName()).get(rule)
              .toString();
          if (!attributeValue.equals(DONTCAREVAL) && !temp.contains(attributeValue)) {
            temp.add(attributeValue);
          }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
      }
      values.put(fields[i].getName(), temp);
      temp = new ArrayList<>();
    }
    return values;
  }

  private String getMajorityValue() {
    int large = 0;
    int majClass = 0;
    String klazz = classes.get(0);
    for(String clazz: classes) {
      for(Rule rule: rules) {
        if(rule.getClassification().equals((clazz))) {
          majClass++;
        }
      }
      if(majClass>large) {
        large = majClass;
        klazz = clazz;
      }
    }
    return klazz;
  }

  public static void main(String[] args) {
    Rule rule0 = new Rule("1", "*", "ok", "false"); // false
    Rule rule1 = new Rule("2", "phone", "*", "false"); // false
    Rule rule2 = new Rule("3", "pc", "nok", "true"); // true
    Rule rule3 = new Rule("3", "tablet", "ok", "true"); // true
    Rule rule4 = new Rule("3", "tablet", "nok", "false"); // true
    List<Rule> rules = new ArrayList<>();
    rules.add(rule0);
    rules.add(rule1);
    rules.add(rule2);
    rules.add(rule3);
    rules.add(rule4);
    Tree tree = new Tree(rules);
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      String json = objectMapper.writeValueAsString(tree.createRBDT1(rules));
      System.out.println(json);
      System.out.println(tree.nodes);
      System.out.println(tree.leafs);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
