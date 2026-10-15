"use client";

import * as React from "react";
import { ScrollArea as ScrollAreaPrimitive } from "@base-ui/react/scroll-area";
import { useVirtualizer } from "@tanstack/react-virtual";
import ReactMarkdown from "react-markdown";
import rehypeRaw from "rehype-raw";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import remarkGfm from "remark-gfm";

import { cn } from "@/lib/utils";
import {
  ScrollArea as InlineScrollArea,
  ScrollBar,
} from "@/components/ui/layout-blocks-utils/scroll-area";
import { IconPlaceholder } from "@/components/ui/layout-blocks-utils/icon-placeholder";

export interface InlineRegistryIconProps extends React.SVGProps<SVGSVGElement> {
  lucide?: string;
  tabler?: string;
  hugeicons?: string;
  phosphor?: string;
  remixicon?: string;
}

function AlignBoxBottomCenterGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="AlignVerticalJustifyEnd"
      tabler="IconAlignBoxBottomCenter"
      hugeicons="AlignBoxBottomCenterIcon"
      phosphor="AlignBottomIcon"
      remixicon="RiAlignBottom"
      {...props}
    />
  );
}
function Heading01Glyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Heading1"
      tabler="IconH1"
      hugeicons="Heading01Icon"
      phosphor="TextHOneIcon"
      remixicon="RiH1"
      {...props}
    />
  );
}
function ImageCompositionGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Image"
      tabler="IconPhoto"
      hugeicons="ImageCompositionIcon"
      phosphor="ImageSquareIcon"
      remixicon="RiImageLine"
      {...props}
    />
  );
}
function LeftToRightListBulletGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Columns3Icon"
      tabler="IconLayoutColumns"
      hugeicons="LeftToRightListBulletIcon"
      phosphor="ColumnsIcon"
      remixicon="RiLayoutColumnLine"
      {...props}
    />
  );
}
function ParagraphGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Pilcrow"
      tabler="IconPilcrow"
      hugeicons="ParagraphIcon"
      phosphor="ParagraphIcon"
      remixicon="RiParagraph"
      {...props}
    />
  );
}
function Table01Glyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Table"
      tabler="IconTable"
      hugeicons="Table01Icon"
      phosphor="TableIcon"
      remixicon="RiTableLine"
      {...props}
    />
  );
}
function TextCenterlineCenterTopGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="PanelTop"
      tabler="IconLayoutNavbar"
      hugeicons="TextCenterlineCenterTopIcon"
      phosphor="AlignTopSimpleIcon"
      remixicon="RiLayoutTopLine"
      {...props}
    />
  );
}
function TextNumberSignGlyph(props: InlineRegistryIconProps) {
  return (
    <IconPlaceholder
      lucide="Hash"
      tabler="IconHash"
      hugeicons="TextNumberSignIcon"
      phosphor="HashIcon"
      remixicon="RiHashtag"
      {...props}
    />
  );
}
export type Point = {
  x: number;
  y: number;
};
export type BoundingBox = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};
export type OcrBlockType =
  | "heading"
  | "paragraph"
  | "list"
  | "table"
  | "figure"
  | "header"
  | "footer"
  | "page_number";
export type ParsedOcrBlock = {
  id: string;
  type: string;
  content: string;
  metadata: {
    page: {
      number: number;
      width: number;
      height: number;
    };
    layoutClass?: string;
    minOcrConfidence?: number;
    avgOcrConfidence?: number;
  };
  polygon?: Point[];
  boundingBox?: BoundingBox;
};
export type ParsedOcrOutput = {
  chunks: {
    blocks: ParsedOcrBlock[];
  }[];
};
export type OcrBlock = {
  id: string;
  type: OcrBlockType;
  text: string;
  page: number;
  pageWidth: number;
  pageHeight: number;
  confidence: number;
  polygon?: Point[];
  boundingBox?: BoundingBox;
};
export type HighlightArea = {
  left: number;
  top: number;
  width: number;
  height: number;
};
export const PDF_URL = "/samples/attention.pdf";
const OCR_BLOCK_ROW_MIN_ESTIMATE = 92;
const OCR_BLOCK_ROW_VERTICAL_CHROME = 62;
const OCR_BLOCK_LINE_HEIGHT = 20;
const OCR_BLOCK_ESTIMATED_CHARS_PER_LINE = 42;
const OCR_BLOCK_ROW_GAP = 8;
const OCR_BLOCK_LIST_PADDING = 12;
const OCR_MARKDOWN_SCHEMA = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    figure: [...(defaultSchema.attributes?.figure ?? []), "type"],
  },
  tagNames: [
    ...(defaultSchema.tagNames ?? []),
    "caption",
    "figcaption",
    "figure",
  ],
};
const OCR_MARKDOWN_REHYPE_PLUGINS: NonNullable<
  React.ComponentProps<typeof ReactMarkdown>["rehypePlugins"]
> = [rehypeRaw, [rehypeSanitize, OCR_MARKDOWN_SCHEMA]];
const OCR_MARKDOWN_REMARK_PLUGINS: NonNullable<
  React.ComponentProps<typeof ReactMarkdown>["remarkPlugins"]
> = [remarkGfm];
const OCR_MARKDOWN_FIGURE_CAPTION_PATTERN = /<\/?caption>/gi;
function getEstimatedOcrBlockRowHeight(block: OcrBlock) {
  const plainText = block.text
    .replace(OCR_MARKDOWN_FIGURE_CAPTION_PATTERN, "")
    .replace(/<[^>]*>/g, "")
    .replace(/\s+/g, " ")
    .trim();
  const explicitLineCount = block.text.split(/\n+/).filter(Boolean).length;
  const wrappedLineCount = Math.max(
    explicitLineCount,
    Math.ceil(plainText.length / OCR_BLOCK_ESTIMATED_CHARS_PER_LINE),
  );
  const contentHeight = Math.max(1, wrappedLineCount) * OCR_BLOCK_LINE_HEIGHT;
  const typeExtraHeight =
    block.type === "figure" ? 44 : block.type === "table" ? 28 : 0;
  return Math.max(
    OCR_BLOCK_ROW_MIN_ESTIMATE,
    OCR_BLOCK_ROW_VERTICAL_CHROME +
      contentHeight +
      typeExtraHeight +
      OCR_BLOCK_ROW_GAP,
  );
}
export const ATTENTION_OCR_OUTPUT = {
  chunks: [
    {
      blocks: [
        {
          id: "block_1_IKDP4i",
          type: "header",
          content: "arXiv:1706.03762v7 [cs.CL] 2 Aug 2023",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Header",
            minOcrConfidence: 0.959,
            avgOcrConfidence: 0.98,
          },
          polygon: [
            {
              x: 36.16179897837395,
              y: 447.38040863840206,
            },
            {
              x: 75.85163708150822,
              y: 447.38040863840206,
            },
            {
              x: 75.85163708150822,
              y: 1155.1796566956025,
            },
            {
              x: 36.16179897837395,
              y: 1155.1796566956025,
            },
          ],
          boundingBox: {
            left: 36.16179897837395,
            top: 447.38040863840206,
            right: 75.85163708150822,
            bottom: 1155.1796566956025,
          },
        },
        {
          id: "block_1_rtNdsr",
          type: "header",
          content:
            "Provided proper attribution is provided, Google hereby grants permission to reproduce the tables and figures in this paper solely for use in journalistic or scholarly works.",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Header",
            minOcrConfidence: 0.992,
            avgOcrConfidence: 0.995,
          },
          polygon: [
            {
              x: 258.50183528705236,
              y: 151.7837261257315,
            },
            {
              x: 1016.8830982960054,
              y: 151.7837261257315,
            },
            {
              x: 1016.8830982960054,
              y: 233.84929126366637,
            },
            {
              x: 258.50183528705236,
              y: 233.84929126366637,
            },
          ],
          boundingBox: {
            left: 258.50183528705236,
            top: 151.7837261257315,
            right: 1016.8830982960054,
            bottom: 233.84929126366637,
          },
        },
        {
          id: "block_1_eo9AoV",
          type: "heading",
          content: "# Attention Is All You Need",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Heading",
            minOcrConfidence: 0.99,
            avgOcrConfidence: 0.994,
          },
          polygon: [
            {
              x: 440.0215455215343,
              y: 312.01490975860366,
            },
            {
              x: 831.4373211268961,
              y: 312.01490975860366,
            },
            {
              x: 831.4373211268961,
              y: 339.0022611402928,
            },
            {
              x: 440.0215455215343,
              y: 339.0022611402928,
            },
          ],
          boundingBox: {
            left: 440.0215455215343,
            top: 312.01490975860366,
            right: 831.4373211268961,
            bottom: 339.0022611402928,
          },
        },
        {
          id: "block_1_Kdt5Cu",
          type: "text",
          content: "Ashish Vaswani* Google Brain avaswani@google.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.977,
            avgOcrConfidence: 0.992,
          },
          polygon: [
            {
              x: 242.9606625633518,
              y: 490.4495616712068,
            },
            {
              x: 449.64758323056856,
              y: 490.4495616712068,
            },
            {
              x: 449.64758323056856,
              y: 555.9028907431696,
            },
            {
              x: 242.9606625633518,
              y: 555.9028907431696,
            },
          ],
          boundingBox: {
            left: 242.9606625633518,
            top: 490.4495616712068,
            right: 449.64758323056856,
            bottom: 555.9028907431696,
          },
        },
        {
          id: "block_1_6Xv6uR",
          type: "text",
          content: "Llion Jones* Google Research llion@google.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.927,
            avgOcrConfidence: 0.982,
          },
          polygon: [
            {
              x: 264.3723327748097,
              y: 594.5307239733245,
            },
            {
              x: 438.3605845653227,
              y: 594.5307239733245,
            },
            {
              x: 438.3605845653227,
              y: 660.7685646688132,
            },
            {
              x: 264.3723327748097,
              y: 660.7685646688132,
            },
          ],
          boundingBox: {
            left: 264.3723327748097,
            top: 594.5307239733245,
            right: 438.3605845653227,
            bottom: 660.7685646688132,
          },
        },
        {
          id: "block_1_Lx35WU",
          type: "text",
          content: "Noam Shazeer* Google Brain noam@google.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.983,
            avgOcrConfidence: 0.991,
          },
          polygon: [
            {
              x: 479.08373644752226,
              y: 490.1169480632122,
            },
            {
              x: 643.7573955006842,
              y: 490.1169480632122,
            },
            {
              x: 643.7573955006842,
              y: 556.2343205043247,
            },
            {
              x: 479.08373644752226,
              y: 556.2343205043247,
            },
          ],
          boundingBox: {
            left: 479.08373644752226,
            top: 490.1169480632122,
            right: 643.7573955006842,
            bottom: 556.2343205043247,
          },
        },
        {
          id: "block_1_VxOJIL",
          type: "text",
          content:
            "Aidan N. Gomez* * University of Toronto aidan@cs.toronto.edu",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.095,
            avgOcrConfidence: 0.875,
          },
          polygon: [
            {
              x: 489.9820146769503,
              y: 593.5908916444707,
            },
            {
              x: 708.1676900821881,
              y: 593.5908916444707,
            },
            {
              x: 708.1676900821881,
              y: 657.7173651788468,
            },
            {
              x: 489.9820146769503,
              y: 657.7173651788468,
            },
          ],
          boundingBox: {
            left: 489.9820146769503,
            top: 593.5908916444707,
            right: 708.1676900821881,
            bottom: 657.7173651788468,
          },
        },
        {
          id: "block_1_m1Gb17",
          type: "text",
          content: "Niki Parmar* Google Research nikip@google.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.99,
            avgOcrConfidence: 0.994,
          },
          polygon: [
            {
              x: 673.3537325893875,
              y: 490.2837284059453,
            },
            {
              x: 848.6601168221802,
              y: 490.2837284059453,
            },
            {
              x: 848.6601168221802,
              y: 556.287735673718,
            },
            {
              x: 673.3537325893875,
              y: 556.287735673718,
            },
          ],
          boundingBox: {
            left: 673.3537325893875,
            top: 490.2837284059453,
            right: 848.6601168221802,
            bottom: 556.287735673718,
          },
        },
        {
          id: "block_1_otnY8T",
          type: "text",
          content: "Łukasz Kaiser* Google Brain lukaszkaiser@google.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.984,
            avgOcrConfidence: 0.992,
          },
          polygon: [
            {
              x: 759.3324257509552,
              y: 594.2661578816579,
            },
            {
              x: 1011.1333304078039,
              y: 594.2661578816579,
            },
            {
              x: 1011.1333304078039,
              y: 660.9298993160851,
            },
            {
              x: 759.3324257509552,
              y: 660.9298993160851,
            },
          ],
          boundingBox: {
            left: 759.3324257509552,
            top: 594.2661578816579,
            right: 1011.1333304078039,
            bottom: 660.9298993160851,
          },
        },
        {
          id: "block_1_ICaos6",
          type: "text",
          content: "Illia Polosukhin* * illia.polosukhin@gmail.com",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.107,
            avgOcrConfidence: 0.766,
          },
          polygon: [
            {
              x: 495.9563429338219,
              y: 697.325802423004,
            },
            {
              x: 778.5449758933407,
              y: 697.325802423004,
            },
            {
              x: 778.5449758933407,
              y: 742.1158899292909,
            },
            {
              x: 495.9563429338219,
              y: 742.1158899292909,
            },
          ],
          boundingBox: {
            left: 495.9563429338219,
            top: 697.325802423004,
            right: 778.5449758933407,
            bottom: 742.1158899292909,
          },
        },
        {
          id: "block_1_zNN0g5",
          type: "section_heading",
          content: "### Abstract",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Subheading",
            minOcrConfidence: 0.995,
            avgOcrConfidence: 0.995,
          },
          polygon: [
            {
              x: 590.8429918498018,
              y: 804.3203170891095,
            },
            {
              x: 683.8031267597728,
              y: 804.3203170891095,
            },
            {
              x: 683.8031267597728,
              y: 823.7656170264223,
            },
            {
              x: 590.8429918498018,
              y: 823.7656170264223,
            },
          ],
          boundingBox: {
            left: 590.8429918498018,
            top: 804.3203170891095,
            right: 683.8031267597728,
            bottom: 823.7656170264223,
          },
        },
        {
          id: "block_1_Pzc7ia",
          type: "text",
          content:
            "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder. The best performing models also connect the encoder and decoder through an attention mechanism. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely. Experiments on two machine translation tasks show these models to be superior in quality while being more parallelizable and requiring significantly less time to train. Our model achieves 28.4 BLEU on the WMT 2014 English- to-German translation task, improving over the existing best results, including ensembles, by over 2 BLEU. On the WMT 2014 English-to-French translation task, our model establishes a new single-model state-of-the-art BLEU score of 41.8 after training for 3.5 days on eight GPUs, a small fraction of the training costs of the best models from the literature. We show that the Transformer generalizes well to other tasks by applying it successfully to English constituency parsing both with large and limited training data.",
          metadata: {
            page: {
              number: 1,
              width: 1275,
              height: 1651,
            },
            layoutClass: "Text",
            minOcrConfidence: 0.987,
            avgOcrConfidence: 0.995,
          },
          polygon: [
            {
              x: 298.2268201173657,
              y: 861.5167879950731,
            },
            {
              x: 978.0244980415288,
              y: 861.5167879950731,
            },
            {
              x: 978.0244980415288,
              y: 1201.6481075430274,
            },
            {
              x: 298.2268201173657,
              y: 1201.6481075430274,
            },
          ],
          boundingBox: {
            left: 298.2268201173657,
            top: 861.5167879950731,
            right: 978.0244980415288,
            bottom: 1201.6481075430274,
          },
        },
      ],
    },
  ],
};
