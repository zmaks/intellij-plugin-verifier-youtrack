package com.jetbrains.pluginverifier.utils;

import com.jetbrains.pluginverifier.problems.Problem;
import com.jetbrains.pluginverifier.problems.UpdateInfo;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.StringWriter;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
@XmlRootElement(name = "results")
public class ResultsElement {

  private String ide;

  private List<Object> problems = new ArrayList<Object>();

  private List<UpdateInfo> updates = new ArrayList<UpdateInfo>();

  private String map = "";

  @XmlAttribute
  public String getIde() {
    return ide;
  }

  public void setIde(String ide) {
    this.ide = ide;
  }

  @NotNull
  public List<Problem> getAllProblems() {
    List<Problem> result = new ArrayList<Problem>();
    for (Object o : this.problems) {
      if (o instanceof Problem) {
        result.add((Problem) o);
      }
    }
    return result;
  }

  /*
  This annotation allows unmarshaller to treat unknown classes as an instance of javax.xml.bind.Element
   */
  @XmlAnyElement(lax = true)
  public List<Object> getProblems() {
    return problems;
  }

  public void setProblems(List<Object> problems) {
    this.problems = new ArrayList<Object>(problems);
  }

  @XmlElementRef
  public List<UpdateInfo> getUpdates() {
    return updates;
  }

  public void setUpdates(List<UpdateInfo> updates) {
    this.updates = updates;
  }

  public String getMap() {
    return map;
  }

  public void setMap(String map) {
    this.map = map;
  }

  public Map<UpdateInfo, Collection<Problem>> asMap() {
    Map<UpdateInfo, Collection<Problem>> res = new LinkedHashMap<UpdateInfo, Collection<Problem>>();

    Scanner sc = new Scanner(map);

    for (UpdateInfo update : updates) {
      Collection<Problem> problems;

      int problemsCount = sc.nextInt();
      if (problemsCount == 0) {
        problems = Collections.emptyList();
      }
      else {
        problems = new ArrayList<Problem>(problemsCount);
        for (int i = 0; i < problemsCount; i++) {

          //Problem class could be unknown to the JAXBContext.
          //So due to @XmlAnyElement(lax=true) unmarshaller will treat
          //this unknown problem as a DOM element (not a Problem class)

          Object o = this.problems.get(sc.nextInt());
          if (o instanceof Problem) {
            problems.add((Problem) o);
          }
        }
      }

      res.put(update, problems);
    }

    return res;
  }

  public void initFromMap(Map<UpdateInfo, Collection<Problem>> map) {
    problems.clear();
    updates.clear();

    StringWriter s = new StringWriter();

    LinkedHashMap<Problem, Integer> problemIndexMap = new LinkedHashMap<Problem, Integer>();

    int idx = 0;

    for (Map.Entry<UpdateInfo, Collection<Problem>> entry : map.entrySet()) {
      UpdateInfo update = entry.getKey();
      Collection<Problem> problemSet = entry.getValue();

      s.append(String.valueOf(problemSet.size()));

      for (Problem problem : problemSet) {
        Integer problemIndex = problemIndexMap.get(problem);

        if (problemIndex == null) {
          problemIndex = idx++;
          problemIndexMap.put(problem, problemIndex);
        }

        s.append(' ').append(String.valueOf(problemIndex));
      }

      s.append('\n');

      updates.add(update);
    }

    problems.addAll(problemIndexMap.keySet());
    this.map = s.toString();
  }
}
