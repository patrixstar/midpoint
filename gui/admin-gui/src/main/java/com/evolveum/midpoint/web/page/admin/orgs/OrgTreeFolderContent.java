package com.evolveum.midpoint.web.page.admin.orgs;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.form.CheckBoxPanel;
import com.evolveum.midpoint.web.component.data.MenuMultiButtonPanel;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.TreeSelectableBean;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.page.admin.users.component.SelectableFolderContent;
import com.evolveum.midpoint.web.session.OrgTreeStateStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrgTreeFolderContent extends BasePanel<TreeSelectableBean<OrgType>> {

    private static final String ID_FOLDER = "folder";
    private static final String ID_MENU = "menu";
    private static final String ID_CHECK = "check";

    //TODO do we need this???
    private IModel<TreeSelectableBean<OrgType>> selected;
    private OrgTreeStateStorage orgTreeStorage;
    private MidpointNestedTree tree;

    private boolean selectable;

    public OrgTreeFolderContent(String id, IModel<TreeSelectableBean<OrgType>> model, boolean selectable, IModel<TreeSelectableBean<OrgType>> selected, MidpointNestedTree tree, OrgTreeStateStorage orgTreeStorage) {
        super(id, model);
        this.selected = selected;
        this.orgTreeStorage = orgTreeStorage;
        this.tree = tree;
        this.selectable = selectable;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {

        SelectableFolderContent folder = new SelectableFolderContent(ID_FOLDER, tree, getModel(), selected) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onClick(Optional<AjaxRequestTarget> target) {
                super.onClick(target);

                OrgTreeFolderContent.this.setSelectedItem(selected.getObject(), orgTreeStorage);

                selectTreeItemPerformed(selected.getObject(), target.get());

                Component component = get("table");
                if (component != null) {
                    target.get().add(component);
                }
            }
        };
        folder.setOutputMarkupId(true);
        add(folder);

        MenuMultiButtonPanel<OrgType> menuButtons = new MenuMultiButtonPanel<>(ID_MENU, new PropertyModel<>(getModel(), TreeSelectableBean.F_VALUE), 2, new PropertyModel<>(getModel(), TreeSelectableBean.F_MENU_ITEMS));
        menuButtons.setOutputMarkupId(true);
        add(menuButtons);

        CheckBoxPanel checkBoxPanel = new CheckBoxPanel(ID_CHECK, getCheckboxModel(getModel())) {

            @Override
            public void onUpdate(AjaxRequestTarget target) {
                getModelObject().setSelected(!getModelObject().isSelected());
                OrgTreeFolderContent.this.onUpdateCheckbox(target);
            }
        };
        checkBoxPanel.add(new VisibleBehaviour(() -> selectable));

        add(checkBoxPanel);
    }

    protected void selectTreeItemPerformed(TreeSelectableBean<OrgType> selected, AjaxRequestTarget target) {

    }

    public void setSelectedItem(TreeSelectableBean<OrgType> item, OrgTreeStateStorage storage){
        if (storage != null){
            storage.setSelectedItem(item);
        }
    }

    protected IModel<Boolean> getCheckboxModel(IModel<TreeSelectableBean<OrgType>> model) {
        return null;
    }

    protected void onUpdateCheckbox(AjaxRequestTarget target) {
    }
}
