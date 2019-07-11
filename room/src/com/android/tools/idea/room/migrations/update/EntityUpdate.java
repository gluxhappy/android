/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.room.migrations.update;

import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the differences between two version of Room database Entity.
 */
public class EntityUpdate {
  private String tableName;
  private List<FieldBundle> deletedFields;
  private List<FieldBundle> newFields;
  private List<FieldBundle> modifiedFields;
  private List<FieldBundle> unmodifiedFields;

  /**
   * @param oldEntity entity description from an older version of the database
   * @param newEntity entity description from the current version of the database
   */
  public EntityUpdate(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    tableName = oldEntity.getTableName();
    deletedFields = new ArrayList<>();
    newFields = new ArrayList<>();
    modifiedFields = new ArrayList<>();
    unmodifiedFields = new ArrayList<>();

    Map<String, FieldBundle> oldEntityFields = new HashMap<>(oldEntity.getFieldsByColumnName());
    for (FieldBundle newField : newEntity.getFields()) {
      if (oldEntityFields.containsKey(newField.getColumnName())) {
        FieldBundle oldField = oldEntityFields.remove(newField.getColumnName());
        if (!oldField.isSchemaEqual(newField)) {
          modifiedFields.add(newField);
        } else {
          unmodifiedFields.add(newField);
        }
      } else {
        newFields.add(newField);
      }
    }
    deletedFields.addAll(oldEntityFields.values());
  }

  @NotNull
  public List<FieldBundle> getDeletedFields() {
    return deletedFields;
  }

  @NotNull
  public List<FieldBundle> getNewFields() {
    return newFields;
  }

  @NotNull
  public List<FieldBundle> getModifiedFields() {
    return modifiedFields;
  }

  @NotNull
  public List<FieldBundle> getUnmodifiedFields() {
    return unmodifiedFields;
  }

  @NotNull
  public String getTableName() {
    return tableName;
  }
}
