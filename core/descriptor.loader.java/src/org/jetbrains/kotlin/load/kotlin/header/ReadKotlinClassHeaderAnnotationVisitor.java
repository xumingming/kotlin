/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.load.kotlin.header;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.SourceElement;
import org.jetbrains.kotlin.load.java.AbiVersionUtil;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.resolve.jvm.JvmClassName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.kotlin.load.java.AbiVersionUtil.isAbiVersionCompatible;
import static org.jetbrains.kotlin.load.java.JvmAnnotationNames.*;
import static org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass.*;
import static org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader.Kind.*;

public class ReadKotlinClassHeaderAnnotationVisitor implements AnnotationVisitor {
    private static final Map<JvmClassName, KotlinClassHeader.Kind> HEADER_KINDS = new HashMap<JvmClassName, KotlinClassHeader.Kind>();
    private static final Map<JvmClassName, KotlinClassHeader.Kind> OLD_DEPRECATED_ANNOTATIONS_KINDS = new HashMap<JvmClassName, KotlinClassHeader.Kind>();

    static {
        HEADER_KINDS.put(KotlinClass.CLASS_NAME, CLASS);
        HEADER_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_PACKAGE), PACKAGE_FACADE);
        HEADER_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_FILE_CLASS), FILE_CLASS);
        HEADER_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_MULTIFILE_CLASS), MULTIFILE_CLASS);
        HEADER_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_MULTIFILE_CLASS_PART), MULTIFILE_CLASS_PART);
        HEADER_KINDS.put(KotlinSyntheticClass.CLASS_NAME, SYNTHETIC_CLASS);

        initOldAnnotations();
    }

    @SuppressWarnings("deprecation")
    private static void initOldAnnotations() {
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_JET_CLASS_ANNOTATION), CLASS);
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_JET_PACKAGE_CLASS_ANNOTATION),
                                             KotlinClassHeader.Kind.PACKAGE_FACADE);
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_KOTLIN_CLASS), CLASS);
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_KOTLIN_PACKAGE), PACKAGE_FACADE);
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_KOTLIN_PACKAGE_FRAGMENT), SYNTHETIC_CLASS);
        OLD_DEPRECATED_ANNOTATIONS_KINDS.put(JvmClassName.byFqNameWithoutInnerClasses(OLD_KOTLIN_TRAIT_IMPL), SYNTHETIC_CLASS);
    }

    private int version = AbiVersionUtil.INVALID_VERSION;
    private String[] annotationData = null;
    private String multifileClassName = null;
    private String[] partNames = null;
    private KotlinClassHeader.Kind headerKind = null;
    private KotlinClass.Kind classKind = null;
    private KotlinSyntheticClass.Kind syntheticClassKind = null;

    @Nullable
    public KotlinClassHeader createHeader() {
        if (headerKind == null) {
            return null;
        }

        if (headerKind == CLASS && classKind == null) {
            // Default class kind is Kind.CLASS
            classKind = KotlinClass.Kind.CLASS;
        }

        if (!AbiVersionUtil.isAbiVersionCompatible(version)) {
            return new KotlinClassHeader(headerKind, version, null, classKind, syntheticClassKind, multifileClassName, partNames);
        }

        if (shouldHaveAnnotationData() && annotationData == null) {
            // This means that the annotation is found and its ABI version is compatible, but there's no "data" string array in it.
            // We tell the outside world that there's really no annotation at all.
            return null;
        }

        if (headerKind == MULTIFILE_CLASS && partNames == null) {
            // Proper KotlinMultifileClass should have partNames.
            // We tell the outside world that there's really no annotation at all.
            return null;
        }

        if (headerKind == MULTIFILE_CLASS_PART && multifileClassName == null) {
            // Proper KotlinMultifileClassPart should have multifileClassName.
            // We tell the outside world that there's really no annotation at all.
            return null;
        }

        return new KotlinClassHeader(headerKind, version, annotationData, classKind, syntheticClassKind, multifileClassName, partNames);
    }

    private boolean shouldHaveAnnotationData() {
        return headerKind == CLASS ||
               headerKind == PACKAGE_FACADE ||
               headerKind == FILE_CLASS ||
               headerKind == MULTIFILE_CLASS ||
               headerKind == MULTIFILE_CLASS_PART;
    }

    @Nullable
    @Override
    public AnnotationArgumentVisitor visitAnnotation(@NotNull ClassId classId, @NotNull SourceElement source) {
        if (headerKind != null) {
            // Ignore all Kotlin annotations except the first found
            return null;
        }

        JvmClassName annotation = JvmClassName.byClassId(classId);

        KotlinClassHeader.Kind newKind = HEADER_KINDS.get(annotation);
        if (newKind != null) {
            headerKind = newKind;

            switch (newKind) {
                case CLASS:
                    return new ClassHeaderReader();
                case PACKAGE_FACADE:
                    return new PackageHeaderReader();
                case FILE_CLASS:
                    return new FileClassHeaderReader();
                case MULTIFILE_CLASS:
                    return new MultifileClassHeaderReader();
                case MULTIFILE_CLASS_PART:
                    return new MultifileClassPartHeaderReader();
                case SYNTHETIC_CLASS:
                    return new SyntheticClassHeaderReader();
                default:
                    throw new IllegalStateException("Unknown kind: " + newKind);
            }
        }

        KotlinClassHeader.Kind oldAnnotationKind = OLD_DEPRECATED_ANNOTATIONS_KINDS.get(annotation);
        if (oldAnnotationKind != null) {
            headerKind = oldAnnotationKind;
        }

        return null;
    }

    @Override
    public void visitEnd() {
    }

    private abstract class HeaderAnnotationArgumentVisitor implements AnnotationArgumentVisitor {
        protected final JvmClassName annotationClassName;

        public HeaderAnnotationArgumentVisitor(@NotNull JvmClassName annotationClassName) {
            this.annotationClassName = annotationClassName;
        }

        @Override
        public void visit(@Nullable Name name, @Nullable Object value) {
            if (name != null) {
                if (name.asString().equals(ABI_VERSION_FIELD_NAME)) {
                    version = value == null ? AbiVersionUtil.INVALID_VERSION : (Integer) value;
                }
                else if (name.asString().equals(MULTIFILE_CLASS_NAME)) {
                    multifileClassName = value == null ? null : (String) value;
                }
                else {
                    unexpectedArgument(name);
                }
            }
            else {
                unexpectedArgument(name);
            }
        }

        @Override
        @Nullable
        public AnnotationArrayArgumentVisitor visitArray(@NotNull Name name) {
            if (name.asString().equals(DATA_FIELD_NAME)) {
                return dataFieldVisitor();
            }
            else if (name.asString().equals(FILE_PART_CLASS_NAMES)) {
                return partNamesFieldVisitor();
            }
            else if (isAbiVersionCompatible(version)) {
                throw new IllegalStateException("Unexpected array argument " + name + " for annotation " + annotationClassName);
            }

            return null;
        }

        @Nullable
        @Override
        public AnnotationArgumentVisitor visitAnnotation(@NotNull Name name, @NotNull ClassId classId) {
            return null;
        }

        @NotNull
        private AnnotationArrayArgumentVisitor dataFieldVisitor() {
            return new StringArrayElementsCollector() {
                @Override
                protected void visitEnd(String[] collected) {
                    annotationData = collected;
                }
            };
        }

        @NotNull
        private AnnotationArrayArgumentVisitor partNamesFieldVisitor() {
            return new StringArrayElementsCollector() {
                @Override
                protected void visitEnd(String[] collected) {
                    partNames = collected;
                }
            };
        }

        @Nullable
        protected AnnotationArrayArgumentVisitor unexpectedArgument(@Nullable Name name) {
            if (isAbiVersionCompatible(version)) {
                throw new IllegalStateException("Unexpected argument " + name + " for annotation " + annotationClassName);
            }
            return null;
        }

        protected void unexpectedEnumArgument(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            if (isAbiVersionCompatible(version)) {
                throw new IllegalStateException("Unexpected enum entry for class annotation " + annotationClassName + ": " +
                                                name + "=" + enumClassId + "." + enumEntryName);
            }
        }

        @Override
        public void visitEnd() {
        }
    }

    private abstract static class StringArrayElementsCollector implements AnnotationArrayArgumentVisitor {
        private final List<String> strings = new ArrayList<String>(1);

        @Override
        public void visit(@Nullable Object value) {
            if (!(value instanceof String)) {
                throw new IllegalStateException("Unexpected argument value: " + value);
            }
            strings.add((String) value);
        }

        @Override
        public void visitEnum(@NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            throw new IllegalStateException("Unexpected enum value in String[]");
        }

        @Override
        public void visitEnd() {
            //noinspection SSBasedInspection
            visitEnd(strings.toArray(new String[strings.size()]));
        }

        protected abstract void visitEnd(String[] collected);
    }

    private class ClassHeaderReader extends HeaderAnnotationArgumentVisitor {
        public ClassHeaderReader() {
            super(KotlinClass.CLASS_NAME);
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            if (KotlinClass.KIND_CLASS_ID.equals(enumClassId) && KIND_FIELD_NAME.equals(name.asString())) {
                classKind = valueOfOrNull(KotlinClass.Kind.class, enumEntryName.asString());
                if (classKind != null) return;
            }
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    private class PackageHeaderReader extends HeaderAnnotationArgumentVisitor {
        public PackageHeaderReader() {
            super(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_PACKAGE));
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    private class FileClassHeaderReader extends HeaderAnnotationArgumentVisitor {
        public FileClassHeaderReader() {
            super(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_FILE_CLASS));
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    private class MultifileClassHeaderReader extends HeaderAnnotationArgumentVisitor {
        public MultifileClassHeaderReader() {
            super(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_MULTIFILE_CLASS));
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    private class MultifileClassPartHeaderReader extends HeaderAnnotationArgumentVisitor {
        public MultifileClassPartHeaderReader() {
            super(JvmClassName.byFqNameWithoutInnerClasses(KOTLIN_MULTIFILE_CLASS_PART));
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    private class SyntheticClassHeaderReader extends HeaderAnnotationArgumentVisitor {
        public SyntheticClassHeaderReader() {
            super(KotlinSyntheticClass.CLASS_NAME);
        }

        @Override
        public void visitEnum(@NotNull Name name, @NotNull ClassId enumClassId, @NotNull Name enumEntryName) {
            if (KotlinSyntheticClass.KIND_CLASS_ID.equals(enumClassId) && KIND_FIELD_NAME.equals(name.asString())) {
                syntheticClassKind = valueOfOrNull(KotlinSyntheticClass.Kind.class, enumEntryName.asString());
                if (syntheticClassKind != null) return;
            }
            unexpectedEnumArgument(name, enumClassId, enumEntryName);
        }
    }

    // This function is needed here because Enum.valueOf() throws exception if there's no such value,
    // but we don't want to fail if we're loading the header with an _incompatible_ ABI version
    @Nullable
    private static <E extends Enum<E>> E valueOfOrNull(@NotNull Class<E> enumClass, @NotNull String entry) {
        try {
            return Enum.valueOf(enumClass, entry);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
