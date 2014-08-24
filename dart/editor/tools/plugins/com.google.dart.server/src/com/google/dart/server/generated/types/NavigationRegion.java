/*
 * Copyright (c) 2014, the Dart project authors.
 *
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * This file has been automatically generated.  Please do not edit it manually.
 * To regenerate the file, use the script "pkg/analysis_server/spec/generate_files".
 */
package com.google.dart.server.generated.types;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.google.common.collect.Lists;
import com.google.dart.server.utilities.general.JsonUtilities;
import com.google.dart.server.utilities.general.ObjectUtilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.lang3.StringUtils;

/**
 * A description of a region from which the user can navigate to the declaration of an element.
 *
 * @coverage dart.server.generated.types
 */
@SuppressWarnings("unused")
public class NavigationRegion {

  public static final NavigationRegion[] EMPTY_ARRAY = new NavigationRegion[0];

  public static final List<NavigationRegion> EMPTY_LIST = Lists.newArrayList();

  /**
   * The offset of the region from which the user can navigate.
   */
  private final Integer offset;

  /**
   * The length of the region from which the user can navigate.
   */
  private final Integer length;

  /**
   * The elements to which the given region is bound. By opening the declaration of the elements,
   * clients can implement one form of navigation.
   */
  private final List<Element> targets;

  /**
   * Constructor for {@link NavigationRegion}.
   */
  public NavigationRegion(Integer offset, Integer length, List<Element> targets) {
    this.offset = offset;
    this.length = length;
    this.targets = targets;
  }

  public boolean containsInclusive(int x) {
    return offset <= x && x <= offset + length;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NavigationRegion) {
      NavigationRegion other = (NavigationRegion) obj;
      return
        other.offset == offset &&
        other.length == length &&
        ObjectUtilities.equals(other.targets, targets);
    }
    return false;
  }

  public static NavigationRegion fromJson(JsonObject jsonObject) {
    Integer offset = jsonObject.get("offset").getAsInt();
    Integer length = jsonObject.get("length").getAsInt();
    List<Element> targets = Element.fromJsonArray(jsonObject.get("targets").getAsJsonArray());
    return new NavigationRegion(offset, length, targets);
  }

  public static List<NavigationRegion> fromJsonArray(JsonArray jsonArray) {
    if (jsonArray == null) {
      return EMPTY_LIST;
    }
    ArrayList<NavigationRegion> list = new ArrayList<NavigationRegion>(jsonArray.size());
    Iterator<JsonElement> iterator = jsonArray.iterator();
    while (iterator.hasNext()) {
      list.add(fromJson(iterator.next().getAsJsonObject()));
    }
    return list;
  }

  /**
   * The length of the region from which the user can navigate.
   */
  public Integer getLength() {
    return length;
  }

  /**
   * The offset of the region from which the user can navigate.
   */
  public Integer getOffset() {
    return offset;
  }

  /**
   * The elements to which the given region is bound. By opening the declaration of the elements,
   * clients can implement one form of navigation.
   */
  public List<Element> getTargets() {
    return targets;
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("offset", offset);
    jsonObject.addProperty("length", length);
    JsonArray jsonArrayTargets = new JsonArray();
    for(Element elt : targets) {
      jsonArrayTargets.add(elt.toJson());
    }
    jsonObject.add("targets", jsonArrayTargets);
    return jsonObject;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    builder.append("offset=");
    builder.append(offset + ", ");
    builder.append("length=");
    builder.append(length + ", ");
    builder.append("targets=");
    builder.append(StringUtils.join(targets, ", "));
    builder.append("]");
    return builder.toString();
  }

}
